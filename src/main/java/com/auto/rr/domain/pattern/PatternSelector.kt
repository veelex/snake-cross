package com.auto.rr.domain.pattern

/**
 * Removes overlapping patterns and stops the same swing being counted twice.
 *
 * Rules:
 *  1. Any double that shares a pivot with a triple is dropped (triples are stronger).
 *  2. Elliott impulses compete with everything else on equal terms (they are ranked below),
 *     unless [elliottOverlapOthers] is set: then they are selected among themselves only and
 *     may share swing points with doubles / triples.
 *  3. Remaining candidates are ranked: confirmed first, then higher quality, then earlier.
 *     A candidate is skipped if any of its pivots is already claimed by an accepted pattern.
 *
 * Result is returned in chronological order; no two patterns share a pivot.
 */
object PatternSelector {

    fun select(
        doubles: List<Pattern>,
        triples: List<Pattern>,
        elliott: List<Pattern> = emptyList(),
        elliottOverlapOthers: Boolean = false
    ): List<Pattern> {
        if (elliottOverlapOthers && elliott.isNotEmpty()) {
            return (select(doubles, triples, emptyList()) + select(emptyList(), emptyList(), elliott))
                .sortedBy { it.leftIndex }
        }

        val triplePivots = HashSet<Int>()
        for (t in triples) triplePivots.addAll(t.pivotIndices)

        val candidates = ArrayList<Pattern>(triples.size + doubles.size + elliott.size)
        candidates.addAll(triples)
        candidates.addAll(elliott)
        for (d in doubles) if (d.pivotIndices.none { it in triplePivots }) candidates += d

        val ranked = candidates.sortedWith(
            compareByDescending<Pattern> { it.isConfirmed }
                .thenByDescending { it.quality }
                .thenBy { it.leftIndex }
        )

        val accepted = ArrayList<Pattern>(ranked.size)
        val claimed = HashSet<Int>()
        for (p in ranked) {
            if (p.pivotIndices.any { it in claimed }) continue
            accepted += p
            claimed.addAll(p.pivotIndices)
        }
        accepted.sortBy { it.leftIndex }
        return accepted
    }
}
