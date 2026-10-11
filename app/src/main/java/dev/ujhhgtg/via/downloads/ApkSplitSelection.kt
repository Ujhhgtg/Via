package dev.ujhhgtg.via.downloads

import java.io.File
import java.util.Locale

internal data class ApkPart(val file: File, val entryName: String, val manifest: ApkManifest, val abis: Set<String>) {
    val isBase get() = manifest.splitName == null
    val group get() = manifest.configForSplit ?: manifest.splitName?.substringBefore(".config.", "").orEmpty()
    val qualifier get() = manifest.splitName.orEmpty().substringAfterLast(".config.").removePrefix("config.")
}

/** InstallerX's device preference order: ABI, density at/above the screen first, then system languages. */
internal object ApkSplitSelection {
    private val densities = mapOf("ldpi" to 120, "mdpi" to 160, "tvdpi" to 213, "hdpi" to 240,
        "xhdpi" to 320, "xxhdpi" to 480, "xxxhdpi" to 640)
    private val architectures = setOf("arm64-v8a", "armeabi-v7a", "armeabi", "x86", "x86_64", "mips", "mips64", "riscv64")
    private val languages = Locale.getISOLanguages().toSet()
    data class Device(val abis: List<String>, val density: Int, val languages: List<String>, val sdk: Int)

    fun abi(part: ApkPart): String? {
        val value = part.qualifier.replace("arm64_v8a", "arm64-v8a").replace("armeabi_v7a", "armeabi-v7a")
        return value.takeIf { it in architectures } ?: if (part.manifest.configForSplit != null || part.manifest.splitName.orEmpty().contains("config.")) part.abis.singleOrNull() else null
    }
    fun density(part: ApkPart): Int? = densities[part.qualifier]
        ?: part.qualifier.removeSuffix("dpi").takeIf { part.qualifier.endsWith("dpi") }?.toIntOrNull()
    fun language(part: ApkPart): String? {
        val value = part.qualifier.replace("-r", "-").replace('_', '-').replace("b+", "").replace('+', '-')
        return value.takeIf { it.substringBefore('-') in languages && it.matches(Regex("[a-z]{2,3}(-[A-Za-z0-9]{2,8})*")) }
            ?.let { Locale.forLanguageTag(it).toLanguageTag() }
    }

    fun bestBase(parts: List<ApkPart>, device: Device): ApkPart? = parts.filter { it.isBase && it.manifest.minSdk <= device.sdk }
        .filter { it.abis.isEmpty() || it.abis.any(device.abis::contains) }
        .sortedWith(compareBy<ApkPart> { part ->
            if (part.abis.isEmpty()) -1 else device.abis.indexOfFirst { it in part.abis }
        }.thenByDescending { it.manifest.versionCode }.thenByDescending { it.manifest.minSdk }).firstOrNull()

    fun compatible(parts: List<ApkPart>, base: ApkPart): List<ApkPart> = parts.filter {
        (it == base || !it.isBase) && it.manifest.packageName == base.manifest.packageName && it.manifest.versionCode == base.manifest.versionCode
    }

    fun recommended(parts: List<ApkPart>, base: ApkPart, device: Device): Set<ApkPart> {
        val result = linkedSetOf(base)
        compatible(parts, base).filter { !it.isBase && it.manifest.minSdk <= device.sdk }.groupBy { it.group }.values.forEach { group ->
            val abi = device.abis.firstOrNull { candidate -> group.any { abi(it) == candidate } }
            val density = group.mapNotNull(::density).distinct().sortedWith(compareBy<Int> { it < device.density }
                .thenBy { if (it >= device.density) it else -it }).firstOrNull()
            val localeTags = device.languages.map { Locale.forLanguageTag(it).toLanguageTag() }
            val availableLanguages = group.mapNotNull(::language).toSet()
            val chosenLanguages = availableLanguages.filter { tag -> localeTags.any {
                it.equals(tag, true) || it.substringBefore('-').equals(tag, true)
            } }.toMutableSet()
            if (chosenLanguages.isEmpty()) {
                val primary = localeTags.firstOrNull()?.substringBefore('-')
                availableLanguages.firstOrNull { it.substringBefore('-') == primary }?.let(chosenLanguages::add)
            }
            group.filter { part -> when {
                abi(part) != null -> abi(part) == abi
                density(part) != null -> density(part) == density
                language(part) != null -> language(part) in chosenLanguages
                else -> true // Feature masters and unqualified resources are needed by default.
            } }.groupBy { it.manifest.splitName }.values.forEach { alternatives ->
                alternatives.maxByOrNull { it.manifest.minSdk }?.let(result::add)
            }
        }
        return result
    }

    fun valid(parts: List<ApkPart>, sdk: Int): Boolean {
        val base = parts.singleOrNull { it.isBase } ?: return false
        val names = parts.mapNotNull { it.manifest.splitName }
        if (names.distinct().size != names.size || base.manifest.splitRequired && names.isEmpty()) return false
        return parts.all { part -> part.manifest.packageName == base.manifest.packageName && part.manifest.versionCode == base.manifest.versionCode &&
            part.manifest.minSdk <= sdk && part.manifest.dependencies.all { it == "base" || it in names } &&
            (part.manifest.configForSplit == null || part.manifest.configForSplit in names) }
    }
}
