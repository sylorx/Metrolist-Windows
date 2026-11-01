package timber.log

/**
 * JVM stand-in for Jake Wharton's Android Timber. The innertube sources call
 * this API and must stay untouched, so the desktop build supplies the same types.
 */
object Timber {
    private val trees = mutableListOf<Tree>()

    fun plant(tree: Tree) {
        trees += tree
    }

    fun d(message: String, vararg args: Any?) = log(3, message, args)
    fun d(t: Throwable?, message: String, vararg args: Any?) = log(3, message, args, t)
    fun i(message: String, vararg args: Any?) = log(4, message, args)
    fun w(message: String, vararg args: Any?) = log(5, message, args)
    fun w(t: Throwable?, message: String, vararg args: Any?) = log(5, message, args, t)
    fun e(message: String, vararg args: Any?) = log(6, message, args)
    fun e(t: Throwable?, message: String, vararg args: Any?) = log(6, message, args, t)

    private fun log(priority: Int, message: String, args: Array<out Any?>, t: Throwable? = null) {
        val text = if (args.isEmpty()) message else runCatching { message.format(*args) }.getOrDefault(message)
        trees.forEach { it.log(priority, null, text, t) }
    }

    abstract class Tree {
        abstract fun log(priority: Int, tag: String?, message: String, t: Throwable?)
    }
}
