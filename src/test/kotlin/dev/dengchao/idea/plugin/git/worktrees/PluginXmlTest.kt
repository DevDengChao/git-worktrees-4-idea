package dev.dengchao.idea.plugin.git.worktrees

import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class PluginXmlTest {
    @Test
    fun `test action installer is registered as a root application listener`() {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(Paths.get("src/main/resources/META-INF/plugin.xml").toFile())
        val root = document.documentElement

        val rootApplicationListeners = root.childElements("applicationListeners")
        assertEquals(1, rootApplicationListeners.size)
        val installerListener = rootApplicationListeners.single()
            .childElements("listener")
            .singleOrNull {
                it.getAttribute("class") ==
                    "dev.dengchao.idea.plugin.git.worktrees.actions.GitLogWorktreeActionInstaller" &&
                    it.getAttribute("topic") == "com.intellij.ide.AppLifecycleListener"
            }
        assertTrue(installerListener != null)

        val nestedApplicationListeners = root.childElements("extensions")
            .flatMap { it.childElements("applicationListeners") }
        assertTrue(nestedApplicationListeners.isEmpty())
    }

    @Test
    fun `test product descriptor declares freemium parameters`() {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(Paths.get("src/main/resources/META-INF/plugin.xml").toFile())
        val root = document.documentElement

        val descriptors = root.childElements("product-descriptor")
        assertEquals("plugin.xml must contain exactly one product-descriptor", 1, descriptors.size)

        val descriptor = descriptors.single()
        assertEquals("PGWFI", descriptor.getAttribute("code"))
        assertEquals("true", descriptor.getAttribute("optional"))
        assertEquals("20260509", descriptor.getAttribute("release-date"))
        assertEquals("2026509", descriptor.getAttribute("release-version"))
    }

    private fun Element.childElements(tagName: String): List<Element> {
        return (0 until childNodes.length)
            .map { childNodes.item(it) }
            .filterIsInstance<Element>()
            .filter { it.tagName == tagName }
    }
}
