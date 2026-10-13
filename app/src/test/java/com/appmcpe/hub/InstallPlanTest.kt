package com.appmcpe.hub

import com.appmcpe.hub.catalog.CfDependency
import com.appmcpe.hub.catalog.CfMod
import com.appmcpe.hub.catalog.DepInfo
import com.appmcpe.hub.catalog.InstallPlan
import com.appmcpe.hub.catalog.Relation
import org.junit.Assert.assertEquals
import org.junit.Test

class InstallPlanTest {
    private fun mod(id: Int, vararg deps: Pair<Int, Int>) = CfMod(
        id = id, name = "m$id", summary = "", author = "", logoUrl = null, downloads = 0,
        fileName = "m$id.mcaddon", downloadUrl = "https://x/$id", sizeBytes = 1,
        dependencies = deps.map { CfDependency(it.first, it.second) },
    )

    private fun infos(vararg mods: CfMod) =
        mods.associate { it.id to DepInfo(it.id, it.name, null, it) }

    @Test
    fun requiredDependenciesComeBeforeTheMainContent_transitively() {
        val lib = mod(3)
        val mid = mod(2, 3 to Relation.REQUIRED)
        val root = mod(1, 2 to Relation.REQUIRED)
        val plan = InstallPlan.buildPlan(root, infos(mid, lib))
        assertEquals(listOf(3, 2, 1), plan.map { it.id })
    }

    @Test
    fun optionalOnlyIncludedWhenSelected_andBringsItsOwnRequirements() {
        val lib = mod(4)
        val opt = mod(3, 4 to Relation.REQUIRED)
        val root = mod(1, 3 to Relation.OPTIONAL)
        val i = infos(opt, lib)
        assertEquals(listOf(1), InstallPlan.buildPlan(root, i).map { it.id })
        assertEquals(listOf(4, 3, 1), InstallPlan.buildPlan(root, i, setOf(3)).map { it.id })
    }

    @Test
    fun cyclesDoNotLoopForever() {
        val a = mod(1, 2 to Relation.REQUIRED)
        val b = mod(2, 1 to Relation.REQUIRED)
        assertEquals(listOf(2, 1), InstallPlan.buildPlan(a, infos(b)).map { it.id })
    }

    @Test
    fun undownloadableRequirementIsReportedAndSkipped() {
        val root = mod(1, 9 to Relation.REQUIRED)
        val i = mapOf(9 to DepInfo(9, "Privado", null, null))
        assertEquals(listOf(1), InstallPlan.buildPlan(root, i).map { it.id })
        assertEquals(listOf(9), InstallPlan.unavailable(root, i).map { it.id })
    }

    @Test
    fun incompatibleAreListedButNeverInstalled() {
        val bad = mod(5)
        val root = mod(1, 5 to Relation.INCOMPATIBLE)
        val i = infos(bad)
        assertEquals(listOf(1), InstallPlan.buildPlan(root, i).map { it.id })
        assertEquals(listOf(5), InstallPlan.incompatible(root, i).map { it.id })
    }
}
