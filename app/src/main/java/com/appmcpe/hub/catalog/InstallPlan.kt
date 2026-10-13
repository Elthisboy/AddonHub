package com.appmcpe.hub.catalog

/**
 * Cálculos puros sobre el grafo de dependencias de un contenido.
 * [infos] es el resultado de [CurseForgeApi.resolveDependencies].
 */
object InstallPlan {

    /** Dependencias opcionales directas del contenido (se muestran con casilla). */
    fun optional(root: CfMod, infos: Map<Int, DepInfo>): List<DepInfo> =
        root.dependencies
            .filter { it.type == Relation.OPTIONAL || it.type == Relation.TOOL }
            .mapNotNull { infos[it.modId] }
            .distinctBy { it.id }

    /** Contenidos incompatibles declarados por el autor. */
    fun incompatible(root: CfMod, infos: Map<Int, DepInfo>): List<DepInfo> =
        root.dependencies
            .filter { it.type == Relation.INCOMPATIBLE }
            .mapNotNull { infos[it.modId] }
            .distinctBy { it.id }

    /**
     * Todo lo requerido (también lo que piden las dependencias), sin repetir.
     * Incluye lo que piden las opcionales ya elegidas en [selected].
     */
    fun required(root: CfMod, infos: Map<Int, DepInfo>, selected: Set<Int> = emptySet()): List<DepInfo> =
        ordered(root, infos, selected).filter { it.id != root.id }
            .mapNotNull { infos[it.id] }
            .filter { dep -> dep.id !in selected }

    /**
     * Orden de instalación: primero las dependencias y al final el contenido principal.
     * Los contenidos sin descarga directa se omiten (la pantalla los avisa aparte).
     */
    fun buildPlan(root: CfMod, infos: Map<Int, DepInfo>, selected: Set<Int> = emptySet()): List<CfMod> =
        ordered(root, infos, selected)

    private fun ordered(root: CfMod, infos: Map<Int, DepInfo>, selected: Set<Int>): List<CfMod> {
        val out = LinkedHashMap<Int, CfMod>()
        val visiting = HashSet<Int>() // evita ciclos entre dependencias

        fun visit(mod: CfMod, isRoot: Boolean) {
            if (mod.id in out || !visiting.add(mod.id)) return
            for (d in mod.dependencies) {
                val wanted = d.type == Relation.REQUIRED ||
                    (isRoot && (d.type == Relation.OPTIONAL || d.type == Relation.TOOL) && d.modId in selected)
                if (!wanted) continue
                infos[d.modId]?.mod?.let { visit(it, isRoot = false) }
            }
            out[mod.id] = mod
        }
        visit(root, isRoot = true)
        return out.values.toList()
    }

    /** Dependencias requeridas que no se pueden descargar desde la app. */
    fun unavailable(root: CfMod, infos: Map<Int, DepInfo>, selected: Set<Int> = emptySet()): List<DepInfo> {
        val seen = HashSet<Int>()
        val out = ArrayList<DepInfo>()
        fun visit(mod: CfMod, isRoot: Boolean) {
            if (!seen.add(mod.id)) return
            for (d in mod.dependencies) {
                val wanted = d.type == Relation.REQUIRED ||
                    (isRoot && (d.type == Relation.OPTIONAL || d.type == Relation.TOOL) && d.modId in selected)
                if (!wanted) continue
                val info = infos[d.modId] ?: continue
                if (info.mod == null) { if (out.none { it.id == info.id }) out += info } else visit(info.mod, false)
            }
        }
        visit(root, true)
        return out
    }
}
