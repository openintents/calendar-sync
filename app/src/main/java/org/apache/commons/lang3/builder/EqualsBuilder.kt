package org.apache.commons.lang3.builder

class EqualsBuilder {
    var equals: Boolean = true

    fun isEquals(): Boolean {
        return equals
    }

    fun append(name: Any, name1: Any): EqualsBuilder {
        equals = equals && name === name1
        return this
    }
}
