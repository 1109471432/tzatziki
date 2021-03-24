package io.nimbly.tzatziki.config

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.updateSettings.impl.UpdateChecker.getNotificationGroup
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import io.nimbly.tzatziki.pdf.PdfStyle
import io.nimbly.tzatziki.psi.getFile
import io.nimbly.tzatziki.util.TzatzikiException
import io.nimbly.tzatziki.util.now
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import java.rmi.server.ExportException
import java.time.format.DateTimeFormatter
import java.util.*
import javax.swing.event.HyperlinkEvent

const val CONFIG_FOLDER = ".cucumber+"
const val PROPERTIES_FILENAME = "cucumber+.properties"
const val PROPERTIES_DEFAULT_FILENAME = "cucumber+.default.properties"
const val CSS_FILENAME = "cucumber+.css"
const val CSS_DEFAULT_FILENAME = "cucumber+.default.css"

fun loadConfig(file: VirtualFile, project: Project): ConfigDTO {

    // Look for root config folder
    val root = ProjectFileIndex.SERVICE.getInstance(project).getSourceRootForFile(file)
        ?: throw TzatzikiException("Please select files from resources")

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();

    var rootConfig = root.findChild(CONFIG_FOLDER)
    if (rootConfig == null) {

        rootConfig = root.createChildDirectory(file, CONFIG_FOLDER)
        rootConfig.copyDefaultsToFolder(project)

        getNotificationGroup().createNotification(
            "Cucumber+", "<html>Configuration <a href='PROP'>files</a> were created</html>",
            NotificationType.INFORMATION
        ) { _: Notification?, _: HyperlinkEvent ->
            PsiManager.getInstance(project).findDirectory(rootConfig)?.navigate(true)
        }.notify(project)
    }

    // Update default files
    rootConfig.updateDefaultFiles(project)

    // Select
    val propertiesFiles = root.loadAllProperties(file)
    val cssFile = root.loadCss(file)

    // Return config
    return createConfiguration(propertiesFiles, cssFile)
}

fun loadConfig(files: List<VirtualFile>, project: Project): ConfigDTO {

    if (files.size == 1)
        return loadConfig(files.first(), project)

    fun VirtualFile.stack(): List<VirtualFile> {
        val list = mutableListOf<VirtualFile>()
        var vf = parent
        while (vf != null) {
            list.add(vf)
            vf = vf.parent
        }
        return list.reversed()
    }

    fun List<*>.allEquals(): Boolean {
        val v = first()
        forEach {
            if (it != v) return false
        }
        return true
    }

    val stacks: List<Iterator<VirtualFile>> = files.map { it.stack().iterator() }

    var common: VirtualFile? = null
    while (true) {

        val nexts: List<VirtualFile> = stacks
            .map { if (it.hasNext()) it.next() else null  }
            .filterNotNull()

        if (nexts.size != stacks.size) break
        if (!nexts.allEquals()) break

        common = nexts.first()
    }

    if (common == null)
        throw TzatzikiException("Selected files does not belongs to same project ?")

    return loadConfig(common, project)
}

private fun VirtualFile.copyDefaultsToFolder(project: Project) {
    WriteCommandAction.runWriteCommandAction(project) {
        listOf(PROPERTIES_DEFAULT_FILENAME, CSS_DEFAULT_FILENAME).forEach {
            if (findChild(it) == null)
                addFrom(it)
        }
    }
}

private fun VirtualFile.addFrom(file: String) {
    val bytes = getResource("/io/nimbly/tzatziki/config/$file")
    createChildData(this, file).setBinaryContent(bytes)
}

private fun VirtualFile.setContentFrom(file: String) {
    val bytes = getResource("/io/nimbly/tzatziki/config/$file")
    setBinaryContent(bytes)
}


private fun getResource(path: String)
    = {}.javaClass.getResourceAsStream(path).readAllBytes()


private fun VirtualFile.updateDefaultFiles(project: Project) {
    listOf(PROPERTIES_DEFAULT_FILENAME, CSS_DEFAULT_FILENAME).forEach { fileName ->

        var currentFile = findChild(fileName)
        if (currentFile == null) {

            // Create file it not exist
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            WriteCommandAction.runWriteCommandAction(project) {
                currentFile = createChildData(this, fileName)
                currentFile!!.setContentFrom(fileName)
            }
            PsiDocumentManager.getInstance(project).commitAllDocuments()

            getNotificationGroup().createNotification(
                "Cucumber+", "<html>Configuration <a href='PROP'>file</a> added</html>",
                NotificationType.INFORMATION) { _: Notification?, _: HyperlinkEvent ->
                PsiManager.getInstance(project).findFile(currentFile!!)?.navigate(true)
            }.notify(project)
        } else {

            // Load from resources
            val hash = {}.javaClass.getResourceAsStream("/io/nimbly/tzatziki/config/$fileName").readAllBytes()!!.contentHashCode()
            if (hash != currentFile!!.contentsToByteArray()!!.contentHashCode()) {

                PsiDocumentManager.getInstance(project).commitAllDocuments()
                WriteCommandAction.runWriteCommandAction(project) {
                    currentFile!!.setContentFrom(fileName)
                }
                PsiDocumentManager.getInstance(project).commitAllDocuments()

                getNotificationGroup().createNotification(
                    "Cucumber+", "<html>Configuration <a href='PROP'>file</a> upated</html>",
                    NotificationType.INFORMATION) { _: Notification?, _: HyperlinkEvent ->
                    PsiManager.getInstance(project).findFile(currentFile!!)?.navigate(true)
                }.notify(project)
            }
        }
    }
}

private fun VirtualFile.loadAllProperties(file: VirtualFile): List<Properties> {

    val all = mutableListOf<Properties>()
    var vf: VirtualFile? = file
    while (vf != null) {

        val folder = vf.findChild(CONFIG_FOLDER)

        if (folder != null) {
            val config = folder.findChild(PROPERTIES_FILENAME)
            if (config != null) {
                val p = Properties()
                p.load(config.inputStream)
                all.add(p)
            }
            if (vf == this) {
                val defaultConfig = folder.findChild(PROPERTIES_DEFAULT_FILENAME)
                if (defaultConfig != null) {
                    val p = Properties()
                    p.load(defaultConfig.inputStream)
                    all.add(p)
                }
            }
        }

        if (vf == this)
            break
        vf = vf.parent
    }

    return all
}

private fun VirtualFile.loadCss(file: VirtualFile): String {

    var vf: VirtualFile? = file
    while (vf != null) {

        val folder = vf.findChild(CONFIG_FOLDER)
        if (folder != null) {
            val css = folder.findChild(CSS_FILENAME)
            if (css != null) {
                return css.contentsToByteArray()!!.toString(Charsets.UTF_8)
            }
            if (vf == this) {
                val defaultCss = folder.findChild(CSS_DEFAULT_FILENAME)
                if (defaultCss != null) {
                    return defaultCss.contentsToByteArray()!!.toString(Charsets.UTF_8)
                }
            }
        }

        if (vf == this)
            break
        vf = vf.parent
    }

    return ""
}


fun createConfiguration(propertiesFiles: List<Properties>, css: String): ConfigDTO {

    fun get(property: String): String {
        propertiesFiles.forEach {
            val v = it.getProperty(property, null)
            if (v != null)
                return v
        }
        return ""
    }

    return ConfigDTO(
        topLeft = get("topLeft"),
        topCenter = get("topCenter"),
        topRight = get("topRight"),
        topFontSize = get("topFontSize"),
        bottomLeft = get("bottomLeft"),
        bottomCenter = get("bottomCenter"),
        bottomRight = get("bottomRight"),
        bottomFontSize = get("bottomFontSize"),
        dateFormat = get("dateFormat"),
        css = css)
}

open class ConfigDTO(
    val topFontSize: String,
    val bottomFontSize: String,

    val topLeft: String,
    val topCenter: String,
    val topRight: String,

    val bottomLeft: String,
    val bottomCenter: String,
    val bottomRight: String,

    val dateFormat: String,

    val css: String) {

    fun buildStyles(): PdfStyle {

        return PdfStyle(
            bodyFontSize = "25px",
            topFontSize = tune(topFontSize),
            bottomFontSize = tune(bottomFontSize),
            topLeft = tune(topLeft),
            topCenter = tune(topCenter),
            topRight = tune(topRight),
            bottomLeft = tune(bottomLeft),
            bottomCenter = tune(bottomCenter),
            bottomRight = tune(bottomRight),
            dateFormat = tune(dateFormat),
            contentStyle = css
        )
    }

    private fun tune(field: String) =
        field.replace("now()", now().format(DateTimeFormatter.ofPattern(dateFormat)))
}

