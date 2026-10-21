from mirror import *
import copy

def approx(a, b, tol=1e-9): return abs(a - b) <= tol

def build_long(w2_end, retest=True, rally_to=1.1250, w1_end=1.1100):
    pts = [(0, 1.1040), (10, 1.1000), (12, w1_end), (10, w2_end)]
    if retest:
        pts += [(4, w1_end - 0.0010), (6, w2_end + 0.0001)]
    pts += [(20, rally_to)]
    return path(pts)

def show(title, sigs):
    print(f"--- {title}: {len(sigs)} signal(s)")
    for s in sigs:
        print(f"    {'BUY ' if s.bullish else 'SELL'} trig={s.trigger} entry={s.entry:.5f} stop={s.stop:.5f} "
              f"tp={s.target:.5f} ratio={s.ratio} R={(abs(s.target-s.entry)/abs(s.entry-s.stop)):.3f}")

ok = True
def check(name, cond):
    global ok
    print(("PASS " if cond else "FAIL ") + name)
    ok &= bool(cond)

# ---------- 1. notes' formulas, both directions, both ratios ----------
yl, yh = 1.1000, 1.1100; d = yh - yl
for r, pct in ((FIB_382, 38.2), (FIB_618, 61.8)):
    up = levels(yl, yh, r, 1.618)         # up wave 1: start=yl, end=yh
    check(f"notes: buy limit  = yh - {pct}%*(yh-yl)", approx(up["entry"], yh - pct * (yh - yl) / 100, 1e-12))
    check(f"notes: stop(up)   = yl (start of wave 1)", approx(up["stop"], yl))
    check(f"notes: PTL(up)    = limit + 1.618*wave1",  approx(up["target"], up["entry"] + 1.618 * d))
    dn = levels(yh, yl, r, 1.618)         # down wave 1: start=yh, end=yl
    check(f"notes: sell limit = yl + {pct}%*(yh-yl)", approx(dn["entry"], yl + pct * (yh - yl) / 100, 1e-12))
    check(f"notes: stop(down) = yh (start of wave 1)", approx(dn["stop"], yh))
    check(f"notes: PTL(down)  = limit - 1.618*wave1",  approx(dn["target"], dn["entry"] - 1.618 * d))
# R multiples implied by the notes
r382 = levels(yl, yh, FIB_382, 1.618); r618 = levels(yl, yh, FIB_618, 1.618)
check("R @38.2% ~= 2.618", approx((r382["target"]-r382["entry"])/(r382["entry"]-r382["stop"]), 1.618/0.618, 1e-9))
check("R @61.8% ~= 4.236", approx((r618["target"]-r618["entry"])/(r618["entry"]-r618["stop"]), 1.618/0.382, 1e-9))
# stop buffer moves stop AWAY from entry in both directions
check("stop buffer (long) is below start", levels(yl, yh, .382, 1.618, 0.0005)["stop"] < yl)
check("stop buffer (short) is above start", levels(yh, yl, .382, 1.618, 0.0005)["stop"] > yh)

# ---------- 2. detection + fill + outcome: long, 38.2% ----------
cs = build_long(1.10618); at = atr_const(cs, 0.0010)
sigs = detect(cs, at, 2, Cfg()); show("long 38.2% / after wave 2", sigs)
check("exactly one setup", len(sigs) == 1)
s = sigs[0]
check("is a buy", s.bullish)
check("ratio matched 0.382", s.ratio == FIB_382)
check("stop == start of wave 1", approx(s.stop, s.p0.price))
check("limit between stop and end of wave 1", s.stop < s.entry < s.p1.price)
check("order is placed only AFTER wave 2 pivot is confirmed (no look-ahead)", s.trigger == s.p2.index + 2)
check("target above wave-1 end", s.target > s.p1.price)
fill = resolve_fill(cs, s); print("    fill:", fill)
check("limit fills on the retest", fill is not None and fill[0] > s.trigger)
res = walk(cs, s, *fill); print("    outcome:", res)
check("trade reaches PTL", res[0] == "TARGET")

# ---------- 3. mirror (short) is symmetric ----------
K = 2.2
csm = [C(K - c.o, K - c.l, K - c.h, K - c.c) for c in cs]
sm = detect(csm, atr_const(csm, 0.0010), 2, Cfg()); show("mirrored -> short", sm)
check("one sell setup", len(sm) == 1 and not sm[0].bullish)
check("sell limit mirrors buy limit", approx(sm[0].entry, K - s.entry, 1e-9))
check("sell stop mirrors", approx(sm[0].stop, K - s.stop, 1e-9))
check("sell PTL mirrors", approx(sm[0].target, K - s.target, 1e-9))
check("sell stop is ABOVE limit; PTL BELOW", sm[0].stop > sm[0].entry > sm[0].target)
fm = resolve_fill(csm, sm[0]); check("sell limit fills on retest", fm is not None)
check("sell reaches PTL", walk(csm, sm[0], *fm)[0] == "TARGET")

# ---------- 4. 61.8% ----------
cs618 = build_long(1.1102 - 0.618 * 0.0104)
s618 = detect(cs618, atr_const(cs618, 0.0010), 2, Cfg()); show("61.8%", s618)
check("auto picks 0.618", len(s618) == 1 and s618[0].ratio == FIB_618)
# forced 38.2 on a 61.8 wave -> nothing
check("forced 38.2% rejects a 61.8% wave", detect(cs618, atr_const(cs618, .001), 2, Cfg(retrace="FIB_382")) == [])
check("forced 61.8% accepts it", len(detect(cs618, atr_const(cs618, .001), 2, Cfg(retrace="FIB_618"))) == 1)

# ---------- 5. wave 2 depth rules ----------
deep = build_long(1.1102 - 0.80 * 0.0104)
check("80% pullback (between levels/too deep) -> no setup", detect(deep, atr_const(deep, .001), 2, Cfg()) == [])
def build_depth(depth):
    """wave-2 end chosen so the PIVOT-measured depth equals `depth` (pivots carry a 3*0.0002 overshoot)."""
    p1, p0 = 1.1100 + 0.0006, 1.1000 - 0.0006
    return build_long((p1 - depth * (p1 - p0)) + 0.0006)
for dd, want in ((0.30, False), (0.33, True), (0.40, True), (0.45, True), (0.50, False), (0.56, True), (0.62, True), (0.67, True), (0.70, False)):
    m = build_depth(dd); got = detect(m, atr_const(m, .001), 2, Cfg())
    check(f"pivot-measured depth {int(dd*100)}% -> setup={want}", bool(got) == want)
mid = build_depth(0.50)
past = build_long(1.0990)    # below the start of wave 1
check("wave 2 past start of wave 1 -> no setup", detect(past, atr_const(past, .001), 2, Cfg()) == [])

# ---------- 6. runaway: price leaves without a retest ----------
run = build_long(1.10618, retest=False, rally_to=1.1300)
sr = detect(run, atr_const(run, .001), 2, Cfg()); show("no retest, rally", sr)
if sr:
    check("order is cancelled when price runs past wave 1 (never fills)", resolve_fill(run, sr[0]) is None)
    nocancel = detect(run, atr_const(run, .001), 2, Cfg(cancel_runaway=False))
    check("without cancel it would only fill if price came back (it doesn't)", resolve_fill(run, nocancel[0]) is None)
else:
    check("(setup skipped because the chance was gone before activation)", True)

# ---------- 7. ANTICIPATE mode fills DURING wave 2 ----------
cs_a = build_long(1.10618, retest=False)
sa = detect(cs_a, atr_const(cs_a, .001), 2, Cfg(timing="ANTICIPATE")); show("anticipate", sa)
check("anticipate: setup exists", len(sa) >= 1)
if sa:
    a = sa[0]
    check("anticipate: placed right after wave 1 confirmed", a.trigger == a.p1.index + 2 and a.p2 is None)
    fa = resolve_fill(cs_a, a); print("    fill:", fa)
    check("anticipate: fills as wave 2 declines", fa is not None)
    low_idx = min(range(a.p1.index, a.p1.index + 25), key=lambda i: cs_a[i].l)
    check("anticipate: fills before wave 2 bottoms out", fa is not None and fa[0] <= low_idx)
    check("anticipate: Auto falls back to 38.2%", a.ratio == FIB_382)

# ---------- 8. gap fill price + same-bar ambiguity ----------
base = Sig(True, 10, 1.1062, 1.1000, 1.1224, 40, 1.1100, .382, P(0,1.1,False), P(5,1.11,True))
gap = [C(1,1,1,1)]*11 + [C(1.1055, 1.1058, 1.1050, 1.1056)]     # gaps below limit
check("buy limit gaps below -> filled at the (better) open", resolve_fill(gap, base) == (11, 1.1055))
both_near_limit = [C(1,1,1,1)]*11 + [C(1.1064, 1.1101, 1.1060, 1.1090)]   # open nearer limit than runaway
check("same bar hits limit & runaway, open nearer limit -> fill", resolve_fill(both_near_limit, base) == (11, 1.1062))
both_near_cancel = [C(1,1,1,1)]*11 + [C(1.1096, 1.1101, 1.1060, 1.1070)]
check("same bar hits both, open nearer runaway -> cancelled", resolve_fill(both_near_cancel, base) is None)
exp = [C(1,1,1,1)]*11 + [C(1.108,1.109,1.1075,1.108)]*40 + [C(1.1062,1.1065,1.1055,1.1060)]
check("order expires (fill after expiry bar ignored)", resolve_fill(exp, base) is None)
sell = Sig(False, 10, 1.1038, 1.1100, 1.0876, 40, 1.1000, .382, P(0,1.11,True), P(5,1.10,False))
sgap = [C(1,1,1,1)]*11 + [C(1.1045, 1.1050, 1.1042, 1.1046)]
check("sell limit gaps above -> filled at the (better) open", resolve_fill(sgap, sell) == (11, 1.1045))

# ---------- 9. min wave-1 size / structure-break filters ----------
check("tiny wave 1 rejected by ATR filter", detect(cs, atr_const(cs, 0.0100), 2, Cfg()) == [])
# prior swing high ABOVE end of wave 1 => wave 1 does not break structure
pts = [(0, 1.1000), (8, 1.1200), (10, 1.1000), (12, 1.1100), (10, 1.10618), (4, 1.1090), (6, 1.1063), (20, 1.1250)]
nb = path(pts)
check("structure-break ON rejects wave 1 under a higher prior high", detect(nb, atr_const(nb,.001), 2, Cfg()) == [])
check("structure-break OFF accepts it", len(detect(nb, atr_const(nb,.001), 2, Cfg(structure_break=False))) >= 1)

print("\nALL PASSED" if ok else "\nSOME FAILED")
raise SystemExit(0 if ok else 1)
