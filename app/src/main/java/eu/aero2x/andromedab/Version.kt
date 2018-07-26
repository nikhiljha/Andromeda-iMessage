package eu.aero2x.andromedab

/**
 * http://stackoverflow.com/a/11024200/1166266
 */
class Version(private val version: String?) : Comparable<Version> {

    fun get(): String {
        return this.version!!
    }

    init {
        if (version == null)
            throw IllegalArgumentException("Version can not be null")
        if (!version.matches("[0-9]+(\\.[0-9]+)*".toRegex()))
            throw IllegalArgumentException("Invalid version format")
    }

    override fun compareTo(that: Version): Int {
        if (that == null)
            return 1
        val thisParts = this.get().split("\\.".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
        val thatParts = that.get().split("\\.".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
        val length = Math.max(thisParts.size, thatParts.size)
        for (i in 0 until length) {
            val thisPart = if (i < thisParts.size)
                Integer.parseInt(thisParts[i])
            else
                0
            val thatPart = if (i < thatParts.size)
                Integer.parseInt(thatParts[i])
            else
                0
            if (thisPart < thatPart)
                return -1
            if (thisPart > thatPart)
                return 1
        }
        return 0
    }

    override fun equals(that: Any?): Boolean {
        if (this === that)
            return true
        if (that == null)
            return false
        return if (this.javaClass != that.javaClass) false else this.compareTo((that as Version?)!!) == 0
    }

}