"""Line-for-line Python mirror of WaveMath / WaveThreeStrategy / BacktestEngine.resolveLimitFill
(plus the existing PivotDetector + PivotNormalizer, without the ATR-prominence filter).
Used only to check the algorithm on synthetic data; the real code is Kotlin."""
from dataclasses import dataclass, field
import math

FIB_382, FIB_618 = 0.382, 0.618

@dataclass
class C: o: float; h: float; l: float; c: float
@dataclass
class P: index: int; price: float; is_high: bool

@dataclass
class Cfg:
    enabled: bool = True
    timing: str = "AFTER_WAVE2"      # or ANTICIPATE
    retrace: str = "AUTO"            # AUTO / FIB_382 / FIB_618
    tol: float = 0.07
    ext: float = 1.618
    stop_buf_atr: float = 0.0
    min_w1_atr: float = 2.0
    min_bars: int = 3
    structure_break: bool = True
    expiry: int = 30
    cancel_runaway: bool = True

@dataclass
class Sig:
    bullish: bool; trigger: int; entry: float; stop: float; target: float
    expiry: int; cancel: float; ratio: float; p0: P; p1: P; p2: P = None

def pivots(cs, s):
    out = []
    for i in range(s, len(cs) - s):
        hi = all(cs[j].h < cs[i].h for j in range(i - s, i + s + 1) if j != i)
        lo = all(cs[j].l > cs[i].l for j in range(i - s, i + s + 1) if j != i)
        if hi: out.append(P(i, cs[i].h, True))
        if lo: out.append(P(i, cs[i].l, False))
    return out

def alternate(pv):
    out = []
    for p in pv:
        if not out or out[-1].is_high != p.is_high: out.append(p); continue
        more = p.price > out[-1].price if p.is_high else p.price < out[-1].price
        if more: out[-1] = p
    return out

def levels(start, end, ratio, ext, buf=0.0):
    d = 1.0 if end > start else -1.0
    size = abs(end - start)
    entry = end - d * ratio * size
    return dict(entry=entry, stop=start - d * buf, target=entry + d * ext * size, runaway=end)

def pick_ratio(depth, mode, tol):
    a, b = FIB_382, FIB_618
    if mode == "FIB_382": return a if abs(depth - a) <= tol else None
    if mode == "FIB_618": return b if abs(depth - b) <= tol else None
    da, db = abs(depth - a), abs(depth - b)
    return (a if da <= tol else None) if da <= db else (b if db <= tol else None)

def gone(cs, frm, to, up, lv, after_w2):
    for i in range(frm, min(to, len(cs) - 1) + 1):
        c = cs[i]
        if after_w2:
            if (c.h >= lv["runaway"]) if up else (c.l <= lv["runaway"]): return True
        else:
            if (c.l <= lv["entry"]) if up else (c.h >= lv["entry"]): return True
    return False

def detect(cs, atr, strength, cfg):
    pv = alternate(pivots(cs, strength))
    n = len(cs); out = []
    for k in range(len(pv) - 1):
        p0, p1 = pv[k], pv[k + 1]
        up = not p0.is_high
        size = (p1.price - p0.price) if up else (p0.price - p1.price)
        if size <= 0: continue
        if p1.index - p0.index < cfg.min_bars: continue
        a1 = atr[p1.index]
        if size < (cfg.min_w1_atr * a1 if a1 > 0 else 0.0015 * abs(p1.price)): continue
        if cfg.structure_break and k >= 1:
            before = pv[k - 1].price
            if (p1.price <= before) if up else (p1.price >= before): continue
        p2 = None
        if cfg.timing == "AFTER_WAVE2":
            if k + 2 >= len(pv): continue
            w2 = pv[k + 2]
            if w2.index - p1.index < cfg.min_bars: continue
            depth = ((p1.price - w2.price) if up else (w2.price - p1.price)) / size
            if depth <= 0 or depth >= 1: continue
            ratio = pick_ratio(depth, cfg.retrace, cfg.tol)
            if ratio is None: continue
            p2 = w2; active = w2.index + strength
        else:
            ratio = FIB_618 if cfg.retrace == "FIB_618" else FIB_382
            active = p1.index + strength
        if active >= n: continue
        at = atr[active]
        buf = cfg.stop_buf_atr * at if at > 0 else 0.0
        lv = levels(p0.price, p1.price, ratio, cfg.ext, buf)
        frm = (p2.index if p2 else p1.index) + 1
        if gone(cs, frm, active, up, lv, p2 is not None): continue
        out.append(Sig(up, active, lv["entry"], lv["stop"], lv["target"], active + cfg.expiry,
                       lv["runaway"] if cfg.cancel_runaway else math.nan, ratio, p0, p1, p2))
    return out

def resolve_fill(cs, s):
    first = s.trigger + 1
    if first >= len(cs): return None
    last = min(s.expiry, len(cs) - 1) if s.expiry >= 0 else len(cs) - 1
    has_cancel = not math.isnan(s.cancel)
    for i in range(first, last + 1):
        c = cs[i]
        touched = c.l <= s.entry if s.bullish else c.h >= s.entry
        ran = has_cancel and ((c.h >= s.cancel) if s.bullish else (c.l <= s.cancel))
        if touched and ran and abs(c.o - s.cancel) < abs(c.o - s.entry): return None
        if touched:
            return i, (min(c.o, s.entry) if s.bullish else max(c.o, s.entry))
        if ran: return None
    return None

def walk(cs, s, idx, px):
    """stop-first, then target, per bar (same order as the engine)."""
    for i in range(idx, len(cs)):
        c = cs[i]
        if s.bullish:
            if c.l <= s.stop: return "STOP", i, s.stop
            if c.h >= s.target: return "TARGET", i, s.target
        else:
            if c.h >= s.stop: return "STOP", i, s.stop
            if c.l <= s.target: return "TARGET", i, s.target
    return "HOLD", len(cs) - 1, cs[-1].c

def path(points, wiggle=0.0002):
    """Candles following a piecewise-linear close path. The last bar of every segment overshoots by
    3*wiggle in its direction, so each swing vertex is a strict pivot (as a real turning bar would be)."""
    cs = []; prev = points[0][1]
    for (n, price) in points[1:]:
        rising = price > prev
        for k in range(1, n + 1):
            pr = prev + (price - prev) * k / n
            o = cs[-1].c if cs else points[0][1]
            hi = max(o, pr) + wiggle; lo = min(o, pr) - wiggle
            if k == n:
                if rising: hi = pr + 3 * wiggle
                else: lo = pr - 3 * wiggle
            cs.append(C(o, hi, lo, pr))
        prev = price
    return cs

def atr_const(cs, v): return [v] * len(cs)
