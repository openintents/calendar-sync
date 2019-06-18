package org.apache.commons.lang3.builder

class HashCodeBuilder(i: Int, i1: Int) : java.lang.Appendable {
    constructor() : this(10,20) {

    }

    override fun append(csq: CharSequence?): HashCodeBuilder {
        sb.append(csq)
        return this
    }

    override fun append(csq: CharSequence?, start: Int, end: Int): HashCodeBuilder {
        sb.append(csq)
        return this
    }

    override fun append(c: Char): HashCodeBuilder {
        sb.append(c)
        return this
    }

    fun append(value: Any): HashCodeBuilder {
        sb.append(value.toString())
        return this
    }

    fun toHashCode(): Int {
        return sb.toString().hashCode()
    }

    val sb = StringBuilder()
}
