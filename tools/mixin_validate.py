"""Validate Sable's mixin selectors against the real 26.2 jar.

Mixin binds by strings and descriptors that javac never checks, so every one of these is a latent
class-load failure that otherwise costs one launch to find. Run it before `runClient`.

    python tools/mixin_validate.py                 # everything
    python tools/mixin_validate.py plot/ Particle  # only paths containing these fragments

What it checks, per @Mixin class registered in a mixins.json:

  registration
    * every mixin source under a config's package is registered somewhere
      (compat.<mod>.* and renderer-variant packages are plugin-gated, so they are exempt)
    * a registered mixin whose @Mixin target cannot be resolved is reported, not skipped

  @Shadow
    * the member exists - on the target or a supertype for a class, on the interface itself for an
      interface, because Mixin does not walk superinterfaces for those
    * a shadowed method's parameters and return type still match
    * a shadowed field's type still matches
    * a prefixed shadow whose target the mixin also implements with the same descriptor is flagged:
      Mixin merges the mixin's version over the target's and the prefixed call recurses forever

  @Accessor / @Invoker
    * the named member exists, whether named explicitly or derived from the method name

  injectors
    * `method = "..."` resolves on the target class *only* - Mixin cannot inject into an inherited
      method - and matches the descriptor when the selector spells one out
    * an @Inject handler's parameters mirror its target's, and it takes CallbackInfo or
      CallbackInfoReturnable to match the target's return type
    * @Overwrite resolves by name *and* parameters

  @At
    * the owner class still exists (a moved one - net.minecraft.Util -> net.minecraft.util.Util -
      is reported rather than dismissed as third-party)
    * the member still exists on it
    * the instruction is still present inside the method the injector selects, with the right
      descriptor and the right owner

That last group is the one worth having. A `method =` selector that stops resolving at least says
"Scanned 0 target(s)"; an @At whose instruction has moved out of the target matches nothing and
dies at class-load with a near-identical message. Player.travel still calls setDeltaMovement - but
the (Vec3) overload - and handleMovePlayer still calls isCreative - but on ServerPlayer, not
ServerPlayerGameMode. Only the bytecode says so, which is why the target method is disassembled.

Not checked: @Local and @At ordinals, @Constant ordinals, slice bounds, and the handler shape of
the non-@Inject injectors, whose parameter lists follow per-annotation rules rather than the
target's. Those still cost a launch.

javap results are cached in tools/.mixin-validate-cache.json, split so a `gradlew build` only
invalidates the mod's own classes and not the game jar. Cold ~90s, warm ~1s.
"""
import re, subprocess, pathlib, os, sys, json, functools

# ModDevGradle, not Loom: the NeoForge build patches Minecraft into its own artifact directory, and
# that jar carries the NeoForge classes too, which the Loom "minecraft-merged" jar did not.
_ROOT = pathlib.Path(__file__).resolve().parent.parent
_MC_JAR = str(_ROOT / "simulated/neoforge/build/moddev/artifacts/minecraft-patched-26.2.0.69-merged.jar")

# The mod's own compiled classes go on the lookup path too: a @Mixin can target a class
# the mod itself owns, and those drift exactly like vanilla ones do. Veil shadowing
# CachedBufferSource.lastSharedType after its own rewrite renamed the field is the case
# that motivated this - invisible while only the game jar was searched.
_OWN_CLASSES = [str(_ROOT / mod / side / "build/classes/java/main")
                for mod in ("simulated", "aeronautics", "offroad")
                for side in ("common", "neoforge")]

# The Create ecosystem, because roughly 150 of Sable's mixins target it and an unresolvable owner is
# silently skipped rather than reported. Read out of the local maven the whole chain is built into.
def _chain_jars():
    m2 = pathlib.Path(os.path.expanduser("~/.m2/repository"))
    wanted = [
        "com/simibubi/create/create-26.2",
        "net/createmod/ponder/ponder-neoforge",
        "net/createmod/catnip/catnip-neoforge",
        "dev/engine-room/flywheel/flywheel-neoforge-26.2",
        "dev/engine-room/flywheel/flywheel-neoforge-api-26.2",
        "com/tterrag/registrate/Registrate",
        "foundry/veil/veil-neoforge-26.2",
        "dev/ryanhcode/sable-companion/sable-companion-common-26.2",
        # Simulated-Project mixes into Sable's internals as well as Create's -- the plan calls those
        # out as public ABI -- so both halves of Sable have to be on the lookup path.
        "dev/ryanhcode/sable/sable-common-26.2",
        "dev/ryanhcode/sable/sable-neoforge-26.2",
    ]
    out = []
    for rel in wanted:
        base = m2 / rel
        if not base.is_dir():
            continue
        jars = sorted(base.rglob("*.jar"))
        jars = [j for j in jars if not j.name.endswith(("-sources.jar", "-javadoc.jar"))]
        if jars:
            out.append(str(jars[-1]))
    return out

_CHAIN = _chain_jars()
JAR = os.pathsep.join([_MC_JAR] + _OWN_CLASSES + _CHAIN)
JAVAP = r"C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot\bin\javap.exe"
SABLE = _ROOT

CONFIGS = [
    (_ROOT / "simulated/common/src/main/resources/simulated.mixins.json", _ROOT / "simulated/common/src/main/java"),
    (_ROOT / "simulated/neoforge/src/main/resources/simulated-neoforge.mixins.json", _ROOT / "simulated/neoforge/src/main/java"),
    (_ROOT / "aeronautics/common/src/main/resources/aeronautics.mixins.json", _ROOT / "aeronautics/common/src/main/java"),
    (_ROOT / "aeronautics/neoforge/src/main/resources/aeronautics.neoforge.mixins.json", _ROOT / "aeronautics/neoforge/src/main/java"),
    (_ROOT / "offroad/common/src/main/resources/offroad.mixins.json", _ROOT / "offroad/common/src/main/java"),
    (_ROOT / "offroad/neoforge/src/main/resources/offroad.neoforge.mixins.json", _ROOT / "offroad/neoforge/src/main/java"),
]


# ---------------------------------------------------------------------------------------------
# javap is the whole cost of this script: one process per target class, six code paths asking for
# the same output. The results only change when the jar or the mod's own classes do, so they are
# memoised in-process and cached on disk between runs, keyed by the mtime+size of every path on
# the lookup classpath. A warm run is essentially free.
# ---------------------------------------------------------------------------------------------

_CACHE_FILE = pathlib.Path(__file__).resolve().parent / ".mixin-validate-cache.json"


def _stamp_of(entry):
    p = pathlib.Path(entry)
    if p.is_file():
        st = p.stat()
        return "%s:%d:%d" % (p.name, st.st_mtime_ns, st.st_size)
    if p.is_dir():
        newest, count = 0, 0
        for f in p.rglob("*.class"):
            st = f.stat()
            newest = max(newest, st.st_mtime_ns)
            count += 1
        return "%s:%d:%d" % (p.name, newest, count)
    return entry + ":missing"


_ENTRIES = JAR.split(os.pathsep)
# Two buckets, because they go stale at different rates. The game jar changes when the Minecraft
# version does - which is to say, never, during a port - while the mod's own classes change on
# every build. Keying them together meant one `gradlew build` threw away every vanilla lookup and
# cost a fresh 90-second sweep; keyed apart, a rebuild only re-reads the mod's own handful.
_VANILLA_STAMP = _stamp_of(_ENTRIES[0])
_OWN_STAMP = "|".join(_stamp_of(e) for e in _ENTRIES[1:])

try:
    _disk = json.loads(_CACHE_FILE.read_text(encoding="utf-8"))
except Exception:
    _disk = {}
if _disk.get("vanillaStamp") != _VANILLA_STAMP:
    _disk["vanillaStamp"], _disk["vanilla"] = _VANILLA_STAMP, {}
if _disk.get("ownStamp") != _OWN_STAMP:
    _disk["ownStamp"], _disk["own"] = _OWN_STAMP, {}
_disk.setdefault("vanilla", {})
_disk.setdefault("own", {})

_javap_dirty = False


def _bucket(fqn):
    return _disk["vanilla"] if fqn.startswith(("net.minecraft.", "com.mojang.")) else _disk["own"]


def javap(fqn, disassemble=False):
    """Cached `javap -p [-c]` stdout for fqn, or None when the class is absent."""
    global _javap_dirty
    bucket = _bucket(fqn)
    key = ("c:" if disassemble else "p:") + fqn
    if key in bucket:
        return bucket[key]

    args = [JAVAP, "-p"] + (["-c"] if disassemble else []) + ["-cp", JAR, fqn]
    r = subprocess.run(args, capture_output=True, text=True)
    out = None if (r.returncode != 0 or "class not found" in (r.stdout + r.stderr)) else r.stdout
    bucket[key] = out
    _javap_dirty = True
    return out


def _save_cache():
    if _javap_dirty:
        try:
            _CACHE_FILE.write_text(json.dumps(_disk), encoding="utf-8")
        except Exception:
            pass


def registered_classes():
    """Every mixin class actually listed in a mixins.json, as a source path."""
    out = []
    for cfg, root in CONFIGS:
        raw = cfg.read_text(encoding="utf-8")
        pkg = re.search(r'"package"\s*:\s*"([^"]+)"', raw).group(1)
        names = re.findall(r'"([A-Za-z_$][\w.$]*Mixin|[A-Za-z_$][\w.$]*(?:Accessor|Invoker))"', raw)
        for n in names:
            rel = (pkg + "." + n).replace(".", "/") + ".java"
            for r in (root, SABLE / "common/src/main/java"):
                p = r / rel
                if p.exists():
                    out.append(p)
                    break
    return sorted(set(out))


_cache = {}


def members(fqn, _seen=None):
    """(fields, methods) declared on fqn and all its supertypes, or None if absent."""
    if fqn in _cache:
        return _cache[fqn]
    if _seen is None:
        _seen = set()
    if fqn in _seen or len(_seen) > 40:
        return (set(), set())
    _seen = _seen | {fqn}

    _src = javap(fqn)
    if _src is None:
        _cache[fqn] = None
        return None

    fields, methods, supers = set(), set(), []
    lines = [l.strip().rstrip(";") for l in _src.splitlines() if l.strip()]
    for line in lines:
        if line.startswith("Compiled"):
            continue
        decl = re.match(r"^(?:\w+ )*(?:class|interface|enum|record) ([\w.$]+)(?:<.*?>)?(.*)$", line)
        if decl and not supers and "(" not in line:
            tail = decl.group(2)
            for kw in ("extends", "implements"):
                mm = re.search(kw + r" (.+?)(?= implements |$)", tail)
                if mm:
                    for s in mm.group(1).split(","):
                        s = re.sub(r"<.*", "", s).strip().rstrip("{").strip()
                        if s and s != "java.lang.Object" and re.fullmatch(r"[\w.$]+", s):
                            supers.append(s)
            continue
        # javap prints "void close() throws java.io.IOException" - the throws clause has to
        # go before the name match, or the line reads as a field named IOException.
        line = re.sub(r"\s+throws\s+[\w.$,\s]+$", "", line)
        mm = re.match(r".*?([\w$]+)\(.*\)$", line)
        if mm:
            methods.add(mm.group(1))
        else:
            mm = re.match(r".*?([\w$]+)$", line)
            if mm:
                fields.add(mm.group(1))

    for s in supers:
        sub = members(s, _seen)
        if sub:
            fields |= sub[0]
            methods |= sub[1]
    res = (fields, methods)
    _cache[fqn] = res
    return res


def _simple(t):
    """'java.util.List<Foo>' / 'net.minecraft.world.entity.Entity' -> 'List' / 'Entity'."""
    t = re.sub(r"<.*>", "", t).strip()
    t = t.replace("...", "[]")
    # javap writes inner classes as Outer$Inner, source writes them Outer.Inner - reduce both to
    # the innermost name so they compare equal.
    return t.split(".")[-1].split("$")[-1].strip()


def invoker_params(src, open_paren_end):
    """The parameter types the mixin itself declares, from just past the opening paren."""
    depth, buf = 1, ""
    for ch in src[open_paren_end:]:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
            if depth == 0:
                break
        buf += ch
    else:
        return None
    raw = buf.strip()
    if not raw:
        return []
    params, depth, cur = [], 0, ""
    for ch in raw:
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth -= 1
        if ch == "," and depth == 0:
            params.append(cur)
            cur = ""
        else:
            cur += ch
    if cur.strip():
        params.append(cur)
    out = []
    for pm in params:
        # "final com.foo.Bar name" -> the type is the second-to-last whitespace token.
        toks = pm.replace("@", " @").split()
        toks = [t for t in toks if not t.startswith("@") and t not in ("final",)]
        if len(toks) < 2:
            return None
        out.append(simple_type(" ".join(toks[:-1])))
    return out


_sig_cache = {}


def signatures(fqn, _seen=None):
    """{methodName: set(tuple-of-simple-param-types)} for fqn and its supertypes."""
    if fqn in _sig_cache:
        return _sig_cache[fqn]
    if _seen is None:
        _seen = set()
    if fqn in _seen or len(_seen) > 40:
        return {}
    _seen = _seen | {fqn}

    _src = javap(fqn)
    if _src is None:
        _sig_cache[fqn] = None
        return None

    out, supers = {}, []
    for line in _src.splitlines():
        line = line.strip().rstrip(";")
        if not line or line.startswith("Compiled"):
            continue
        decl = re.match(r"^(?:\w+ )*(?:class|interface|enum|record) ([\w.$]+)(?:<.*?>)?(.*)$", line)
        if decl and not supers and "(" not in line:
            for kw in ("extends", "implements"):
                mm = re.search(kw + r" (.+?)(?= implements |$)", decl.group(2))
                if mm:
                    for s in mm.group(1).split(","):
                        s = re.sub(r"<.*", "", s).strip().rstrip("{").strip()
                        if s and s != "java.lang.Object" and re.fullmatch(r"[\w.$]+", s):
                            supers.append(s)
            continue
        line = re.sub(r"\s+throws\s+[\w.$,\s]+$", "", line)
        mm = re.match(r"^(.*?)([\w$]+)\((.*)\)$", line)
        if not mm:
            continue
        name, params = mm.group(2), mm.group(3).strip()
        # javap prints generics as <T> before the return type; strip any leading type-parameter list
        parts = []
        if params:
            depth, cur = 0, ""
            for ch in params:
                if ch == "<":
                    depth += 1
                elif ch == ">":
                    depth -= 1
                if ch == "," and depth == 0:
                    parts.append(cur)
                    cur = ""
                else:
                    cur += ch
            parts.append(cur)
        out.setdefault(name, set()).add(tuple(_simple(p) for p in parts))

    for s in supers:
        sub = signatures(s, _seen)
        if sub:
            for k, v in sub.items():
                out.setdefault(k, set()).update(v)
    _sig_cache[fqn] = out
    return out



def _params_match(declared, candidate):
    """Element-wise, treating javap's generic type variables as wildcards.

    javap prints a generic parameter as its type variable ("T"), while the mixin source has to
    write the erased bound ("Entity"). Comparing those literally reports a mismatch that is not
    one, so a single-letter candidate type matches anything.
    """
    if len(declared) != len(candidate):
        return False
    for d, c in zip(declared, candidate):
        if d == c:
            continue
        base = c.rstrip("[]")
        if len(base) <= 2 and base[:1].isupper() and base.isalnum():
            continue  # type variable: T, S, E, T1 ...
        return False
    return True


def _source_params(param_src):
    """Parameter types, as simple names, from a Java parameter list."""
    parts, depth, cur = [], 0, ""
    for ch in param_src:
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append(cur)
            cur = ""
        else:
            cur += ch
    if cur.strip():
        parts.append(cur)
    types = []
    for p in parts:
        p = re.sub(r"@[\w.]+(\([^)]*\))?", "", p).strip()       # annotations, qualified or not
        p = re.sub(r"^(final|volatile)\s+", "", p).strip()
        toks = p.split()
        if len(toks) < 2:
            return None  # can't parse confidently - skip the check
        types.append(_simple(" ".join(toks[:-1])))
    return tuple(types)


_body_cache = {}


def body_refs(fqn):
    """{methodName: set(referencedMemberNames)} from the disassembly of fqn.

    This is what makes an @At checkable rather than merely plausible. A selector that resolves
    only proves the *method* still exists; the injector also needs the instruction it anchors to
    to still be inside that method, and nothing in the Java source says whether it is. A stale
    anchor matches nothing and, under defaultRequire=1, kills the game at class-load.
    """
    if fqn in _body_cache:
        return _body_cache[fqn]

    _src = javap(fqn, disassemble=True)
    if _src is None:
        _body_cache[fqn] = None
        return None

    out, cur = {}, None
    for line in _src.splitlines():
        decl = re.match(r"^  [\w<].*?([\w$<>]+)\(.*\)", line)
        if decl and not line.strip().startswith(("Code:", "//")):
            cur = out.setdefault(decl.group(1), set())
            continue
        if cur is None:
            continue
        # "  85: invokevirtual #1797  // Method net/minecraft/world/level/Level.getFluidState:(...)"
        # Record the bare name, name+descriptor, and owner+name+descriptor. All three matter:
        #   - a stale descriptor is as fatal as a stale name (Player.travel calls the (Vec3)
        #     overload of setDeltaMovement, not (DDD))
        #   - so is a stale owner (handleMovePlayer reads ServerPlayer.isCreative now, not
        #     ServerPlayerGameMode.isCreative - same name, same descriptor, different class)
        # javap omits the owner for same-class calls, which the check below accounts for.
        mm = re.search(r"//\s*(?:Method|InterfaceMethod|Field)\s+(?:([\w/$]+)\.)?([\w$<>]+):(\S+)", line)
        if mm:
            owner, name, desc = mm.group(1), mm.group(2), mm.group(3)
            cur.add(name)
            cur.add(name + ":" + desc)
            if owner:
                cur.add(owner + "." + name + ":" + desc)
    _body_cache[fqn] = out
    return out


# Injector annotations that carry a `method =` selector plus an @At.
_INJECTOR = re.compile(
    r"@(Inject|Redirect|ModifyArg|ModifyArgs|ModifyVariable|ModifyConstant|WrapOperation"
    r"|ModifyReturnValue|WrapWithCondition|ModifyExpressionValue)\s*\(")


def _annotation_blocks(src):
    """Yield the full text of each injector annotation, parentheses balanced."""
    for m in _INJECTOR.finditer(src):
        i = m.end() - 1
        depth, j, in_str = 0, i, False
        while j < len(src):
            ch = src[j]
            if in_str:
                if ch == "\\":
                    j += 2
                    continue
                if ch == '"':
                    in_str = False
            elif ch == '"':
                in_str = True
            elif ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    break
            j += 1
        yield src[m.start():j + 1]


_ret_cache = {}


def returns(fqn, _seen=None):
    """{methodName: set(simple return type)} for fqn and its supertypes."""
    if fqn in _ret_cache:
        return _ret_cache[fqn]
    if _seen is None:
        _seen = set()
    if fqn in _seen or len(_seen) > 40:
        return {}
    _seen = _seen | {fqn}

    _src = javap(fqn)
    if _src is None:
        _ret_cache[fqn] = None
        return None

    out, supers = {}, []
    for line in _src.splitlines():
        line = line.strip().rstrip(";")
        if not line or line.startswith("Compiled"):
            continue
        decl = re.match(r"^(?:\w+ )*(?:class|interface|enum|record) ([\w.$]+)(?:<.*?>)?(.*)$", line)
        if decl and not supers and "(" not in line:
            for kw in ("extends", "implements"):
                mm = re.search(kw + r" (.+?)(?= implements |$)", decl.group(2))
                if mm:
                    for s in mm.group(1).split(","):
                        s = re.sub(r"<.*", "", s).strip().rstrip("{").strip()
                        if s and s != "java.lang.Object" and re.fullmatch(r"[\w.$]+", s):
                            supers.append(s)
            continue
        line = re.sub(r"\s+throws\s+[\w.$,\s]+$", "", line)
        mm = re.match(r"^(.*?)\s([\w$]+)\(.*\)$", line)
        if not mm:
            continue
        head, name = mm.group(1), mm.group(2)
        # strip modifiers and any leading generic type-parameter list
        head = re.sub(r"^(public|private|protected|static|final|abstract|default|synchronized|native|strictfp)\s+", "", head)
        while re.match(r"^(public|private|protected|static|final|abstract|default|synchronized|native|strictfp)\s+", head):
            head = re.sub(r"^(public|private|protected|static|final|abstract|default|synchronized|native|strictfp)\s+", "", head)
        head = re.sub(r"^<.*?>\s*", "", head)
        out.setdefault(name, set()).add(_simple(head) if head else "void")

    for s in supers:
        sub = returns(s, _seen)
        if sub:
            for k, v in sub.items():
                out.setdefault(k, set()).update(v)
    _ret_cache[fqn] = out
    return out


_decl_cache = {}


def declared_members(fqn):
    """(fields, methods) declared directly on fqn - no supertype walk.

    Mixin resolves @Shadow against an interface target's own members only: an interface mixin
    cannot shadow something it merely inherits from a superinterface. BlockAndTintGetter extends
    BlockAndLightGetter, and shadowing getLightEngine through it fails at class-load.
    """
    if fqn in _decl_cache:
        return _decl_cache[fqn]

    _src = javap(fqn)
    if _src is None:
        _decl_cache[fqn] = None
        return None

    fields, methods, is_interface = set(), set(), False
    for line in _src.splitlines():
        line = line.strip().rstrip(";")
        if not line or line.startswith("Compiled"):
            continue
        if re.match(r"^(?:\w+ )*(?:class|interface|enum|record) ", line) and "(" not in line:
            is_interface = " interface " in " " + line
            continue
        line = re.sub(r"\s+throws\s+[\w.$,\s]+$", "", line)
        mm = re.match(r".*?([\w$]+)\(.*\)$", line)
        if mm:
            methods.add(mm.group(1))
        else:
            mm = re.match(r".*?([\w$]+)$", line)
            if mm:
                fields.add(mm.group(1))
    res = (fields, methods, is_interface)
    _decl_cache[fqn] = res
    return res


_sig_cache = {}


def simple_type(t):
    """A type's last identifier, so a mixin's source spelling and javap's output compare.

    javap prints com.mojang.blaze3d.vertex.PoseStack$Pose where the mixin source says
    PoseStack.Pose; both reduce to Pose. Generics are erased and varargs become the element type,
    which is what the descriptor carries anyway. Arrays keep their brackets.
    """
    t = t.strip()
    t = re.sub(r"<.*?>", "", t)
    t = t.replace("...", "[]")
    arr = ""
    while t.endswith("[]"):
        arr += "[]"
        t = t[:-2].strip()
    t = re.split(r"[.$]", t)[-1]
    return t + arr


def method_signatures(fqn, _seen=None):
    """name -> set of parameter-type tuples, declared on fqn or any supertype.

    Names alone are not enough for @Invoker: Mixin matches the *descriptor*, so an invoker whose
    parameters have changed resolves by name and then fails at class-load with "No candidates were
    found matching". ChainConveyorShape.drawOutline taking a PoseStack.Pose instead of a PoseStack
    is the case that got away -- it crashed the game the first time a chain conveyor was placed.
    """
    key = fqn
    if key in _sig_cache:
        return _sig_cache[key]
    if _seen is None:
        _seen = set()
    if fqn in _seen or len(_seen) > 40:
        return {}
    _seen = _seen | {fqn}

    _src = javap(fqn)
    if _src is None:
        _sig_cache[key] = None
        return None

    out, supers = {}, []
    for line in _src.splitlines():
        line = line.strip().rstrip(";")
        if not line or line.startswith("Compiled"):
            continue
        decl = re.match(r"^(?:\w+ )*(?:class|interface|enum|record) ([\w.$]+)(?:<.*?>)?(.*)$", line)
        if decl and "(" not in line:
            tail = decl.group(2)
            for kw in ("extends", "implements"):
                mm = re.search(kw + r" (.+?)(?= implements |$)", tail)
                if mm:
                    for sup in mm.group(1).split(","):
                        sup = re.sub(r"<.*", "", sup).strip().rstrip("{").strip()
                        if sup and sup != "java.lang.Object" and re.fullmatch(r"[\w.$]+", sup):
                            supers.append(sup)
            continue
        line = re.sub(r"\s+throws\s+[\w.$,\s]+$", "", line)
        mm = re.match(r".*?([\w$]+)\((.*)\)$", line)
        if not mm:
            continue
        raw = mm.group(2).strip()
        # Split on commas outside generics, so Map<K, V> stays one parameter.
        params, depth, cur = [], 0, ""
        for ch in raw:
            if ch == "<":
                depth += 1
            elif ch == ">":
                depth -= 1
            if ch == "," and depth == 0:
                params.append(cur)
                cur = ""
            else:
                cur += ch
        if cur.strip():
            params.append(cur)
        out.setdefault(mm.group(1), set()).add(tuple(simple_type(x) for x in params))

    for sup in supers:
        sub = method_signatures(sup, _seen)
        if sub:
            for k, v in sub.items():
                out.setdefault(k, set()).update(v)
    _sig_cache[key] = out
    return out


_ftype_cache = {}


def field_types(fqn, _seen=None):
    """{fieldName: set(simple type)} for fqn and its supertypes.

    A @Shadow field whose *type* stopped matching is as fatal as one whose name did -
    AbstractFurnaceBlockEntity.recipesUsed went Object2IntOpenHashMap<Identifier> ->
    Reference2IntOpenHashMap<ResourceKey<Recipe<?>>> and the name never moved.
    """
    if fqn in _ftype_cache:
        return _ftype_cache[fqn]
    if _seen is None:
        _seen = set()
    if fqn in _seen or len(_seen) > 40:
        return {}
    _seen = _seen | {fqn}

    _src = javap(fqn)
    if _src is None:
        _ftype_cache[fqn] = None
        return None

    out, supers = {}, []
    for line in _src.splitlines():
        line = line.strip().rstrip(";")
        if not line or line.startswith("Compiled"):
            continue
        decl = re.match(r"^(?:\w+ )*(?:class|interface|enum|record) ([\w.$]+)(?:<.*?>)?(.*)$", line)
        if decl and not supers and "(" not in line:
            for kw in ("extends", "implements"):
                mm = re.search(kw + r" (.+?)(?= implements |$)", decl.group(2))
                if mm:
                    for s in mm.group(1).split(","):
                        s = re.sub(r"<.*", "", s).strip().rstrip("{").strip()
                        if s and s != "java.lang.Object" and re.fullmatch(r"[\w.$]+", s):
                            supers.append(s)
            continue
        if "(" in line:
            continue
        mm = re.match(r"^(.*?)\s([\w$]+)$", line)
        if not mm:
            continue
        head, name = mm.group(1), mm.group(2)
        while re.match(r"^(public|private|protected|static|final|transient|volatile)\s+", head):
            head = re.sub(r"^(public|private|protected|static|final|transient|volatile)\s+", "", head)
        if head:
            out.setdefault(name, set()).add(_simple(head))

    for s in supers:
        sub = field_types(s, _seen)
        if sub:
            for k, v in sub.items():
                out.setdefault(k, set()).update(v)
    _ftype_cache[fqn] = out
    return out


_DESC_PRIMS = {"Z": "boolean", "B": "byte", "C": "char", "S": "short",
               "I": "int", "J": "long", "F": "float", "D": "double", "V": "void"}


def _desc_params(desc):
    """"(Lnet/minecraft/Foo;DDZ)V" -> ("Foo", "double", "double", "boolean"), or None."""
    if not desc.startswith("("):
        return None
    body = desc[1:desc.index(")")]
    out, i = [], 0
    while i < len(body):
        arr = 0
        while i < len(body) and body[i] == "[":
            arr += 1
            i += 1
        if i >= len(body):
            return None
        c = body[i]
        if c == "L":
            end = body.index(";", i)
            name = _simple(body[i + 1:end].replace("/", "."))
            i = end + 1
        elif c in _DESC_PRIMS:
            name = _DESC_PRIMS[c]
            i += 1
        else:
            return None
        out.append(name + "[]" * arr)
    return tuple(out)


_UNRESOLVED = []


def resolve_target(src, body):
    imports = dict(re.findall(r"^import\s+(?:static\s+)?([\w.]+\.(\w+));", src, re.M))
    # Wildcard imports have to be resolved too. Without this a single "import
    # net.minecraft.server.level.*;" makes the target unresolvable, and an unresolvable target
    # meant the whole file was skipped in silence - ServerChunkCacheMixin went unchecked that way
    # while its selector had been dead since the port started.
    wildcards = re.findall(r"^import\s+([\w.]+)\.\*;", src, re.M)
    out = []
    for tok in re.findall(r"([\w.$]+)\.class", body):
        simple = tok.split(".")[0]
        for full, s in imports.items():
            if s == simple:
                out.append(full + tok[len(simple):].replace(".", "$"))
                break
        else:
            if "." in tok:
                out.append(tok)
                continue
            for pkg in wildcards:
                candidate = pkg + "." + tok.replace(".", "$")
                if members(candidate) is not None:
                    out.append(candidate)
                    break
            else:
                if wildcards:
                    # Named in a wildcard-imported package but resolvable in none of them: say so
                    # rather than skipping the file, which is what used to happen.
                    _UNRESOLVED.append((src, tok, tuple(wildcards)))
    for tok in re.findall(r'targets\s*=\s*"([^"]+)"', body):
        out.append(tok)
    return out


# ModifyExpressionValue was missing here while being present in the @At scan above, so its
# `method =` selectors were never checked for existence at all -- which is how
# DepotRendererMixin's "renderItem" survived a clean run and then failed at class-load.
ANN = r"@(?:Inject|Redirect|ModifyArg|ModifyArgs|ModifyVariable|ModifyConstant|WrapOperation|ModifyReturnValue|WrapMethod|WrapWithCondition|ModifyExpressionValue)"

problems = []
checked = 0
# Optional path filters: `python tools/mixin_validate.py plot/ ParticleMixin` checks only the
# registered mixins whose source path contains one of those substrings, which turns a full sweep
# into a sub-second check of what you just edited. With no arguments it checks everything, which
# is what a pre-launch sweep wants.
_FILTERS = [a.replace("\\", "/") for a in sys.argv[1:] if not a.startswith("-")]


def _selected(path):
    if not _FILTERS:
        return True
    s = str(path).replace("\\", "/")
    return any(f in s for f in _FILTERS)


_all = registered_classes()
_scanned = [q for q in _all if _selected(q)]
for p in _scanned:
    src = p.read_text(encoding="utf-8", errors="replace")
    # Strip block comments: a commented-out mixin is not registered against anything, and
    # reporting its selectors is noise. Line comments are left alone - they never hold one.
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    m = re.search(r"@Mixin\s*\(\s*(?:value\s*=\s*)?\{?([^)]*?)\}?\s*\)", src)
    if not m:
        continue
    targets = resolve_target(src, m.group(1))
    if not targets:
        # A registered mixin whose target cannot be resolved is not "fine" - it is unchecked.
        problems.append((str(p).replace("\\", "/").split("/java/")[-1],
                         "@Mixin unresolved", m.group(1).strip(), ["?"]))
        continue
    resolved = [(t, members(t)) for t in targets]
    if all(r is None for _, r in resolved):
        continue  # third-party / excluded target, reported by the other checker
    fields = set().union(*[r[0] for _, r in resolved if r])
    methods = set().union(*[r[1] for _, r in resolved if r])
    short = str(p).replace("\\", "/").split("/java/")[-1]

    # @Shadow members
    for mm in re.finditer(r"@Shadow([^;{]*?)\s([\w$]+)\s*(\(|;|=)", src):
        attrs, name, kind = mm.group(1), mm.group(2), mm.group(3)
        checked += 1
        # A shadowed member may be declared under a prefix so it can coexist with a same-named
        # method the mixin implements itself. Mixin's default prefix is "shadow$".
        pm = re.search(r'prefix\s*=\s*"([^"]*)"', attrs)
        prefix = pm.group(1) if pm else "shadow$"
        if prefix and name.startswith(prefix):
            name = name[len(prefix):]
        pool = methods if kind == "(" else fields
        if name not in pool and name not in ("class",):
            problems.append((short, "@Shadow " + ("method" if kind == "(" else "field"), name, targets))

    # @Shadow field types.
    ftypes = {}
    for t4 in targets:
        ft = field_types(t4)
        if ft:
            for k, v in ft.items():
                ftypes.setdefault(k, set()).update(v)
    if ftypes:
        for mm in re.finditer(r"@Shadow([^;{]*?)\n?\s*(?:public|private|protected|static|final|transient|volatile|\s)*"
                              r"([\w.<>\[\]$?, ]+?)\s+([\w$]+)\s*(?:=[^;]*)?;", src):
            attrs, ftype, name = mm.group(1), mm.group(2), mm.group(3)
            if "(" in attrs or "(" in ftype:
                continue
            pm = re.search(r'prefix\s*=\s*"([^"]*)"', attrs)
            prefix = pm.group(1) if pm else "shadow$"
            if prefix and name.startswith(prefix):
                name = name[len(prefix):]
            if name not in ftypes:
                continue  # already reported by the name check
            # The capture can still carry annotations and modifiers ("@Final private Minecraft").
            # After generics are stripped the type is the last whitespace-separated token.
            declared_type = _simple(ftype)
            declared_type = declared_type.split()[-1] if declared_type.split() else declared_type
            checked += 1
            if not any(_params_match((declared_type,), (c,)) for c in ftypes[name]):
                problems.append((short, "@Shadow field type",
                                 "%s %s, want %s" % (declared_type, name, "/".join(sorted(ftypes[name]))),
                                 targets))

    # Interface targets: @Shadow and @Overwrite resolve against the interface's own members.
    iface_pools = []
    for t3 in targets:
        d = declared_members(t3)
        if d and d[2]:
            iface_pools.append((t3, d))
    if iface_pools:
        own_fields = set().union(*[d[0] for _, d in iface_pools])
        own_methods = set().union(*[d[1] for _, d in iface_pools])
        for mm in re.finditer(r"@Shadow([^;{]*?)\s([\w$]+)\s*(\(|;|=)", src):
            attrs, name, kind = mm.group(1), mm.group(2), mm.group(3)
            pm = re.search(r'prefix\s*=\s*"([^"]*)"', attrs)
            prefix = pm.group(1) if pm else "shadow$"
            if prefix and name.startswith(prefix):
                name = name[len(prefix):]
            pool = own_methods if kind == "(" else own_fields
            if name in ("class",):
                continue
            checked += 1
            if name not in pool:
                problems.append((short, "@Shadow on iface", name, [t for t, _ in iface_pools]))

    # @Overwrite carries no selector - the annotated method's own name is the target.
    pools = []
    for t3 in targets:
        d = declared_members(t3)
        if d:
            pools.append(d[1] if d[2] else members(t3)[1])
    if pools:
        overwrite_methods = set().union(*pools)
        # The generic bound in "private static <E extends Entity & Leashable> void foo(...)" has to
        # be inside the type character class, or the whole declaration fails to match and the
        # @Overwrite goes unchecked.
        for mm in re.finditer(r"@Overwrite[^\n]*\n(?:\s*@\w+[^\n]*\n)*\s*(?:public|private|protected|default|static|final|abstract|\s)*"
                              r"[\w.<>\[\]$?,&| ]+?\s+([\w$]+)\s*\(([^)]*)\)", src):
            name = mm.group(1)
            checked += 1
            if name not in overwrite_methods:
                problems.append((short, "@Overwrite", name, targets))
            declared_ow = _source_params(mm.group(2))
            if declared_ow is None:
                continue
            # An @Overwrite replaces one exact method. A parameter that widened - RandomPos's
            # radius went int -> double - leaves the name resolving while the overwrite binds to
            # nothing, and that only shows up when something first calls it.
            cands_ow = set()
            for t7 in targets:
                s7 = signatures(t7)
                if s7 and name in s7:
                    cands_ow |= s7[name]
            if cands_ow and not any(_params_match(declared_ow, c) for c in cands_ow):
                checked += 1
                problems.append((short, "@Overwrite sig",
                                 "%s(%s), want %s" % (name, ", ".join(declared_ow),
                                                      " | ".join("(" + ", ".join(c) + ")" for c in sorted(cands_ow))),
                                 targets))

    # @Shadow method descriptors.
    #
    # A @Shadow whose name survives but whose parameters changed compiles fine and then fails at
    # class-load with "was not located in the target class" - Player.canFallAtLeast going
    # (double, double, float) -> (double, double, double) is the motivating case. The Java source
    # gives a real signature here, so unlike a `method =` string it can actually be compared.
    #
    # Matching is on erased simple type names, which is enough to catch a changed parameter and
    # cheap enough not to need a full descriptor resolver.
    sigs = {}
    for t in targets:
        s = signatures(t)
        if s:
            for k, v in s.items():
                sigs.setdefault(k, set()).update(v)
    if sigs:
        for mm in re.finditer(r"@Shadow([^;{]*?)\s([\w$]+)\s*\(([^)]*)\)\s*;", src):
            attrs, name, params = mm.group(1), mm.group(2), mm.group(3)
            pm = re.search(r'prefix\s*=\s*"([^"]*)"', attrs)
            prefix = pm.group(1) if pm else "shadow$"
            if prefix and name.startswith(prefix):
                name = name[len(prefix):]
            if name not in sigs:
                continue  # already reported by the name check
            declared = _source_params(params)
            if declared is None:
                continue
            checked += 1
            if not any(_params_match(declared, cand) for cand in sigs[name]):
                shown = "%s(%s)" % (name, ", ".join(declared))
                problems.append((short, "@Shadow sig", shown, targets))

    # @Shadow method return types.
    #
    # The parameter check above says nothing about what comes back, and a return type can move on
    # its own: Frustum.cubeInFrustum kept its six doubles and went boolean -> int, which reads at
    # class-load as "was not located in the target class".
    if targets:
        rets_all = {}
        for t6 in targets:
            rr = returns(t6)
            if rr:
                for k, v in rr.items():
                    rets_all.setdefault(k, set()).update(v)
        if rets_all:
            for mm in re.finditer(r"@Shadow([^;{]*?)\n?\s*(?:public|private|protected|abstract|static|final|\s)*"
                                  r"([\w.<>\[\]$?,&| ]+?)\s+([\w$]+)\s*\([^)]*\)\s*;", src):
                attrs, rtype, name = mm.group(1), mm.group(2), mm.group(3)
                pm = re.search(r'prefix\s*=\s*"([^"]*)"', attrs)
                prefix = pm.group(1) if pm else "shadow$"
                if prefix and name.startswith(prefix):
                    name = name[len(prefix):]
                if name not in rets_all:
                    continue  # already reported by the name check
                declared_ret = _simple(rtype)
                declared_ret = declared_ret.split()[-1] if declared_ret.split() else declared_ret
                checked += 1
                if not any(_params_match((declared_ret,), (c,)) for c in rets_all[name]):
                    problems.append((short, "@Shadow return",
                                     "%s %s(), want %s" % (declared_ret, name, "/".join(sorted(rets_all[name]))),
                                     targets))

    # A prefixed @Shadow whose target the mixin also implements is an infinite recursion.
    #
    # The prefix exists so a mixin can reach the original when it declares a same-named method. That
    # only works when the descriptors differ: if they match, Mixin merges the mixin's version over
    # the target's, and the prefixed call resolves to the merged override instead of the original.
    # Veil's PipelinePoseStackMixin shadowed isEmpty() while implementing MatrixStack.isEmpty(), and
    # it surfaced as a StackOverflowError on world join - nowhere near the mixin.
    for mm in re.finditer(r"@Shadow([^;{]*?)\s(?:public|private|protected|abstract|static|final|\s)*"
                          r"([\w.<>\[\]$?,&| ]+?)\s+([\w$]+)\s*\(([^)]*)\)\s*;", src):
        attrs, name, params = mm.group(1), mm.group(3), mm.group(4)
        pm = re.search(r'prefix\s*=\s*"([^"]*)"', attrs)
        prefix = pm.group(1) if pm else "shadow$"
        if not (prefix and name.startswith(prefix)):
            continue
        bare = name[len(prefix):]
        shadow_params = _source_params(params)
        if shadow_params is None:
            continue
        # does this mixin class implement the un-prefixed name with the same parameter list?
        for om in re.finditer(r"\n\s*(?:@Override\s*\n\s*)?(?:public|private|protected|default|final|\s)+"
                              r"[\w.<>\[\]$?,&| ]+?\s+" + re.escape(bare) + r"\s*\(([^)]*)\)\s*\{", src):
            own_params = _source_params(om.group(1))
            if own_params is not None and own_params == shadow_params:
                checked += 1
                problems.append((short, "@Shadow recurses",
                                 "%s%s -> %s(%s), which this mixin also implements"
                                 % (prefix, bare, bare, ", ".join(own_params)), targets))
                break

    # @Accessor / @Invoker with explicit name
    for mm in re.finditer(r'@(Accessor|Invoker)\s*\(\s*"([^"]+)"\s*\)', src):
        checked += 1
        name = mm.group(2)
        pool = methods if mm.group(1) == "Invoker" else fields
        if name not in pool and name not in methods:
            problems.append((short, "@" + mm.group(1), name, targets))

    # @Accessor / @Invoker without an explicit name: Mixin derives it from the method name, so
    # getEntityEffect() means the field "entityEffect" and invokeFoo() means the method "foo".
    # Only the explicit-string form was checked before, which let a getter for a deleted field
    # through - Veil's LevelRenderer.entityEffect is the case that got away.
    for mm in re.finditer(r"@(Accessor|Invoker)\s*(?:\(\s*\))?\s*\n(?:\s*@[\w.]+(?:\([^)]*\))?\s*\n)*"
                          r"\s*(?:public|private|protected|abstract|default|static|\s)*"
                          r"[\w.<>\[\]$?,&| ]+?\s+([\w$]+)\s*\(", src):
        kind, method = mm.group(1), mm.group(2)
        stem = re.match(r"^(get|set|is|invoke|call)([A-Z]\w*)$", method)
        if not stem:
            continue
        name = stem.group(2)[0].lower() + stem.group(2)[1:]
        pool = methods if kind == "Invoker" else fields
        checked += 1
        if name not in pool and method not in pool:
            problems.append((short, "@%s derived" % kind, "%s -> %s" % (method, name), targets))
        elif kind == "Invoker":
            # The name resolves; now check the descriptor. Mixin matches an invoker on parameter
            # types too, and a mismatch is only reported at class-load, as a crash.
            want = invoker_params(src, mm.end())
            if want is not None:
                checked += 1
                have, known = set(), False
                for td in targets:
                    sigs = method_signatures(td)
                    if sigs is None:
                        continue
                    known = True
                    have |= sigs.get(name, set()) | sigs.get(method, set())
                if known and have and tuple(want) not in have:
                    problems.append((short, "@Invoker params",
                                     "%s(%s) -> no %s(%s)"
                                     % (method, ", ".join(want), name,
                                        "), (".join(", ".join(h) for h in sorted(have))),
                                     targets))

    # method = "..." selectors
    for mm in re.finditer(ANN + r"\s*\((?:[^()]|\([^()]*\))*?method\s*=\s*(\{[^}]*\}|\"[^\"]*\")", src, re.S):
        for sel in re.findall(r'"([^"]+)"', mm.group(1)):
            checked += 1
            # Mixin also accepts fully-qualified selectors, "Lowner/Class;name(desc)ret".
            # Drop the owner so the bare name is what gets looked up.
            name = sel.split("(")[0].split("*")[0].strip()
            if ";" in name:
                name = name.rsplit(";", 1)[1]
            if not name or name.startswith("<"):
                continue
            # Mixin resolves an injector's `method =` against the *target class only* - it cannot
            # inject into a method the target merely inherits. TerrainParticle stopped overriding
            # getLightCoords and the selector kept resolving against Particle's copy, which reads at
            # class-load as "could not find any targets matching".
            own = set()
            for td in targets:
                d = declared_members(td)
                if d:
                    own |= d[1]
            if name not in (own or methods):
                problems.append((short, "method=", sel, targets))

            # A selector may spell out the descriptor - "teleportTo(...ZLjava/util/Set;FFZ)Z" - and
            # then it has to track the target exactly. Mixin reports this as "could not find any
            # targets matching", which reads like a missing method even though the name is fine.
            if "(" in sel and name in methods:
                want = _desc_params(sel[sel.index("("):])
                if want is not None:
                    cands = set()
                    for t5 in targets:
                        s5 = signatures(t5)
                        if s5 and name in s5:
                            cands |= s5[name]
                    if cands:
                        checked += 1
                        if not any(_params_match(want, c) for c in cands):
                            problems.append((short, "selector desc",
                                             "%s(%s)" % (name, ", ".join(want)), targets))

    # @At(target = "Lowner/Class;member...") - the instruction an injector anchors to.
    #
    # This is the other half of the problem, and the nastier half: a `method =` selector that stops
    # resolving is at least reported as "0 targets scanned", but an @At whose target no longer
    # exists just matches nothing, and with defaultRequire=1 that surfaces as an identical-looking
    # failure one boot at a time. PoseStack.mulPose going Quaternionf -> Quaternionfc is the
    # motivating case.
    #
    # Only owners that resolve in the jar are checked. An unresolvable owner is a third-party or
    # LWJGL class (remap = false), which this has no business judging.
    for mm in re.finditer(r'target\s*=\s*"(L[\w/$]+;[^"]+)"', src):
        raw = mm.group(1)
        owner, rest = raw[1:].split(";", 1)
        owner_fqn = owner.replace("/", ".")
        om = members(owner_fqn)
        if om is None:
            # An owner under net/minecraft or com/mojang that does not resolve is a moved or
            # deleted class, not a third-party one - and a moved class is the commonest cause of a
            # silently dead @At (net.minecraft.Util -> net.minecraft.util.Util, and friends).
            # Anything else really is out of scope: LWJGL, JOML, other mods.
            if (owner.startswith("net/minecraft/")
                    or (owner.startswith("com/mojang/") and not owner.startswith("com/mojang/brigadier/"))):
                checked += 1
                problems.append((short, "@At owner gone", raw, [owner_fqn]))
            continue
        checked += 1
        member = rest.split("(")[0].split(":")[0]
        if member.startswith("<"):
            continue
        pool = om[1] if "(" in rest else om[0]
        if member not in pool:
            problems.append((short, "@At target", raw, [owner_fqn]))

    # @At anchors that resolve but are no longer *in* the method they point into.
    #
    # Player.travel still exists and Entity.setDeltaMovement(DDD) still exists, but travel stopped
    # calling it - so a @Redirect between the two binds to nothing. Neither the name checks above
    # nor the compiler can see that; only the target method's bytecode can.
    for block in _annotation_blocks(src):
        sel_m = re.search(r"method\s*=\s*(\{[^}]*\}|\"[^\"]*\")", block)
        if not sel_m:
            continue
        names = []
        for sel in re.findall(r'\"([^\"]+)\"', sel_m.group(1)):
            n = sel.split("(")[0].strip()
            if ";" in n:
                n = n.rsplit(";", 1)[1]
            if n and "*" not in n:
                names.append(n)
        if not names:
            continue

        refs = set()
        known = False
        for t in targets:
            br = body_refs(t)
            if br is None:
                continue
            for n in names:
                if n in br:
                    known = True
                    refs |= br[n]
        if not known:
            continue  # method not found - already reported by the selector check

        for tm in re.finditer(r'target\s*=\s*\"(L[\w/$]+;[^\"]+)\"', block):
            raw = tm.group(1)
            owner, rest = raw[1:].split(";", 1)
            # No owner filter here, unlike the member check above. This one only asks "is this
            # referenced inside the target method", which needs the *target's* disassembly and not
            # the owner's - so an LWJGL or JOML owner is just as checkable as a game one, and
            # GlStateManager's generators moving from GL15/GL30 to GL33C is exactly the sort of
            # thing that hides behind an owner filter.
            if owner.startswith("com/mojang/brigadier/"):
                continue
            member = rest.split("(")[0].split(":")[0]
            if not member or member.startswith("<"):
                continue
            # If the @At spelled out a descriptor, hold it to that; a bare name is checked as one.
            desc = rest[len(member):].lstrip(":")
            probe = member + ":" + desc if desc else member
            checked += 1
            if probe not in refs:
                shown = "%s%s in %s" % (member, "(" + desc.split(")")[0].lstrip("(") + ")" if desc else "",
                                        "/".join(names))
                problems.append((short, "@At not in body", shown, targets))
            elif desc:
                # The name and descriptor are present - but is it on the class the @At names? Only
                # judge this when the body records an owner for that member at all; javap prints
                # same-class calls unqualified, and those are legitimately owner-less.
                qualified = [r for r in refs if r.endswith("." + member + ":" + desc)]
                if qualified and (owner + "." + member + ":" + desc) not in refs:
                    checked += 1
                    problems.append((short, "@At wrong owner",
                                     "%s.%s in %s, body calls %s" % (
                                         owner.split("/")[-1], member, "/".join(names),
                                         ", ".join(sorted({q.rsplit(".", 1)[0].split("/")[-1] for q in qualified}))),
                                     targets))

    # @Inject handler parameters vs the target method's.
    #
    # An @Inject handler must repeat the target's parameter list before its CallbackInfo. When a
    # target's signature changes - Entity.load(CompoundTag) becoming load(ValueInput) - the handler
    # still compiles, because nothing in Java ties the two together, and Mixin rejects it at
    # class-load with "Invalid descriptor". Comparing them here is straightforward.
    if sigs:
        for m in re.finditer(r"@Inject\s*\(", src):
            # the annotation block, then the method declaration that follows it
            i, depth, j, in_str = m.end() - 1, 0, m.end() - 1, False
            while j < len(src):
                ch = src[j]
                if in_str:
                    if ch == "\\":
                        j += 2
                        continue
                    if ch == '"':
                        in_str = False
                elif ch == '"':
                    in_str = True
                elif ch == "(":
                    depth += 1
                elif ch == ")":
                    depth -= 1
                    if depth == 0:
                        break
                j += 1
            block = src[m.start():j + 1]
            decl = re.search(r"\)\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:public|private|protected|\s)*"
                             r"[\w.<>\[\]$?, ]+?\s+([\w$]+)\s*\(([^;{]*?)\)\s*\{",
                             src[j:j + 4000], re.S)
            if not decl:
                continue
            handler_params = _source_params(decl.group(2))
            if handler_params is None:
                continue

            sel_m = re.search(r"method\s*=\s*(\{[^}]*\}|\"[^\"]*\")", block)
            if not sel_m:
                continue
            names = []
            for sel in re.findall(r'\"([^\"]+)\"', sel_m.group(1)):
                n = sel.split("(")[0].strip()
                if ";" in n:
                    n = n.rsplit(";", 1)[1]
                if not n or n.startswith("<"):
                    continue
                if "*" not in n:
                    names.append(n)
                elif n.endswith("*") and "*" not in n[:-1]:
                    # A trailing wildcard is a prefix match. It is worth resolving rather than
                    # skipping: simulated's "setupAnim*" matched a method whose whole parameter list
                    # 26.2 replaced, and skipping the selector skipped the check that would have
                    # said so. Only an unambiguous prefix is usable.
                    matched = [k for k in sigs if k.startswith(n[:-1])]
                    if len(matched) == 1:
                        names.append(matched[0])
            if len(names) != 1 or names[0] not in sigs:
                continue  # ambiguous or already reported

            # everything before the CallbackInfo is meant to mirror the target's parameters
            cut = None
            for idx, tp in enumerate(handler_params):
                if tp in ("CallbackInfo", "CallbackInfoReturnable"):
                    cut = idx
                    break
            if cut is None:
                continue  # not the standard shape - leave it alone

            # CallbackInfo vs CallbackInfoReturnable. Mixin picks between them by the target's
            # return type, and gets it wrong loudly: "CallbackInfoReturnable is required!".
            rets = set()
            for t2 in targets:
                rr = returns(t2)
                if rr and names[0] in rr:
                    rets |= rr[names[0]]
            if rets:
                wants_cir = rets != {"void"}
                has_cir = handler_params[cut] == "CallbackInfoReturnable"
                checked += 1
                if wants_cir != has_cir:
                    problems.append((short, "@Inject callback",
                                     "%s returns %s but handler takes %s"
                                     % (names[0], "/".join(sorted(rets)), handler_params[cut]),
                                     targets))

            declared = handler_params[:cut]
            if not declared:
                # Mixin lets a callback drop the target's arguments entirely and take only the
                # CallbackInfo. That is a legal, common shape - not a mismatch.
                continue

            checked += 1
            if not any(_params_match(declared, cand) for cand in sigs[names[0]]):
                want = " | ".join("(" + ", ".join(c) + ")" for c in sorted(sigs[names[0]]))
                problems.append((short, "@Inject params",
                                 "%s(%s) want %s" % (names[0], ", ".join(declared), want), targets))

def orphan_mixins():
    """Mixin sources that exist on disk but appear in no mixins.json.

    A mixin nobody registered is dead code that fails silently: the class is never applied, the
    interface it adds is never present, and the first thing to cast to that interface gets a
    ClassCastException far from the cause. Veil's PreparedRenderTypeMixin was written for 26.2 and
    never added to veil.rendertype.mixins.json, which surfaced as a crash in the first rendered
    frame after joining a world.
    """
    registered = {q.resolve() for q in registered_classes()}
    found = []
    for cfg, root in CONFIGS:
        raw = cfg.read_text(encoding="utf-8")
        pkg = re.search(r'"package"\s*:\s*"([^"]+)"', raw).group(1)
        pkg_dir = root / pkg.replace(".", "/")
        if not pkg_dir.is_dir():
            continue
        for src_file in pkg_dir.rglob("*.java"):
            if src_file.resolve() in registered:
                continue
            # compat.<modid>.* mixins are gated by the mixin plugin on that mod being loaded, so
            # whether one is registered at all is a deliberate per-mod call, not an oversight.
            rel = str(src_file).replace("\\", "/")
            if "/compat/" in rel:
                continue
            # Renderer-variant packages are selected at load time by the mixin plugin - Sable picks
            # sublevel_render/impl/sodium or impl/vanilla depending on whether Sodium is present -
            # so the unselected half is unregistered on purpose.
            if "/impl/sodium/" in rel or "/impl/vanilla/" in rel:
                continue
            body = src_file.read_text(encoding="utf-8", errors="replace")
            body = re.sub(r"/\*.*?\*/", "", body, flags=re.S)
            mm = re.search(r"@Mixin\s*\(\s*(?:value\s*=\s*)?\{?([^)]*?)\}?\s*\)", body)
            if not mm:
                continue
            # Compat mixins for mods that are not on the classpath are unregistered on purpose, and
            # there are a lot of them. They give themselves away: their target does not resolve.
            # Anything targeting a class that *does* resolve is an oversight worth reporting.
            tgts = resolve_target(body, mm.group(1))
            if tgts and any(members(t) is not None for t in tgts):
                found.append((src_file, cfg.name))
    return sorted(set(found))


for _orphan, _cfg in orphan_mixins():
    if not _selected(_orphan):
        continue
    problems.append((str(_orphan).replace("\\", "/").split("/java/")[-1],
                     "not registered", "no entry in " + _cfg, ["-"]))
    checked += 1

print("=== MISSING MEMBERS (%d)" % len(problems))
by_file = {}
for short, kind, name, targets in problems:
    by_file.setdefault(short, []).append((kind, name, targets[0] if targets else "?"))
for f in sorted(by_file):
    print("\n  " + f)
    for kind, name, tgt in sorted(set(by_file[f])):
        print("      %-16s %-64s -> %s" % (kind, name, tgt.split(".")[-1]))
_save_cache()
if _FILTERS:
    print("\nchecked %d selectors across %d of %d registered mixin classes (filtered by %s)"
          % (checked, len(_scanned), len(_all), ", ".join(_FILTERS)))
else:
    print("\nchecked %d selectors across %d registered mixin classes" % (checked, len(_scanned)))
