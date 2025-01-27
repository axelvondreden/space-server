package de.axl.serialization.alto

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "alto")
data class Alto(
    @JacksonXmlProperty(localName = "Layout")
    val layout: AltoLayout
)

data class AltoLayout(
    @JacksonXmlProperty(localName = "Page")
    val page: AltoPage
)

data class AltoPage(
    @JacksonXmlProperty(isAttribute = true, localName = "WIDTH")
    val width: String,
    @JacksonXmlProperty(isAttribute = true, localName = "HEIGHT")
    val height: String,
    @JacksonXmlProperty(localName = "PrintSpace")
    val printSpace: AltoPrintSpace
)

class AltoPrintSpace {
    @JacksonXmlProperty(isAttribute = true, localName = "HPOS")
    var hpos: String = ""

    @JacksonXmlProperty(isAttribute = true, localName = "VPOS")
    var vpos: String = ""

    @JacksonXmlProperty(isAttribute = true, localName = "WIDTH")
    var width: String = ""

    @JacksonXmlProperty(isAttribute = true, localName = "HEIGHT")
    var height: String = ""

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "GraphicalElement")
    var graphicalElements: List<AltoGraphicalElement> = emptyList()
        set(value) {
            field = field.plus(value)
        }

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Illustration")
    var illustrations: List<AltoIllustration> = emptyList()
        set(value) {
            field = field.plus(value)
        }

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "ComposedBlock")
    var composedBlocks: List<AltoComposedBlock> = emptyList()
        set(value) {
            field = field.plus(value)
        }
}

data class AltoIllustration(
    @JacksonXmlProperty(isAttribute = true, localName = "HPOS")
    val hpos: String,
    @JacksonXmlProperty(isAttribute = true, localName = "VPOS")
    val vpos: String,
    @JacksonXmlProperty(isAttribute = true, localName = "WIDTH")
    val width: String,
    @JacksonXmlProperty(isAttribute = true, localName = "HEIGHT")
    val height: String
)

data class AltoGraphicalElement(
    @JacksonXmlProperty(isAttribute = true, localName = "HPOS")
    val hpos: String,
    @JacksonXmlProperty(isAttribute = true, localName = "VPOS")
    val vpos: String,
    @JacksonXmlProperty(isAttribute = true, localName = "WIDTH")
    val width: String,
    @JacksonXmlProperty(isAttribute = true, localName = "HEIGHT")
    val height: String
)

class AltoComposedBlock {
    @JacksonXmlProperty(isAttribute = true, localName = "HPOS")
    var hpos: String = ""

    @JacksonXmlProperty(isAttribute = true, localName = "VPOS")
    var vpos: String = ""

    @JacksonXmlProperty(isAttribute = true, localName = "WIDTH")
    var width: String = ""

    @JacksonXmlProperty(isAttribute = true, localName = "HEIGHT")
    var height: String = ""

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "TextBlock")
    var textBlocks: List<AltoTextBlock> = emptyList()
        set(value) {
            field = field.plus(value)
        }
}

class AltoTextBlock {
    @JacksonXmlProperty(isAttribute = true, localName = "HPOS")
    var hpos: String = ""

    @JacksonXmlProperty(isAttribute = true, localName = "VPOS")
    var vpos: String = ""

    @JacksonXmlProperty(isAttribute = true, localName = "WIDTH")
    var width: String = ""

    @JacksonXmlProperty(isAttribute = true, localName = "HEIGHT")
    var height: String = ""

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "TextLine")
    var textLines: List<AltoTextLine> = emptyList()
        set(value) {
            field = field.plus(value)
        }
}

class AltoTextLine {
    @JacksonXmlProperty(isAttribute = true, localName = "HPOS")
    var hpos: String = ""

    @JacksonXmlProperty(isAttribute = true, localName = "VPOS")
    var vpos: String = ""

    @JacksonXmlProperty(isAttribute = true, localName = "WIDTH")
    var width: String = ""

    @JacksonXmlProperty(isAttribute = true, localName = "HEIGHT")
    var height: String = ""

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "String")
    var words: List<AltoString> = emptyList()
        set(value) {
            field = field.plus(value)
        }
}

class AltoString {
    @JacksonXmlProperty(isAttribute = true, localName = "HPOS")
    var hpos: String = ""

    @JacksonXmlProperty(isAttribute = true, localName = "VPOS")
    var vpos: String = ""

    @JacksonXmlProperty(isAttribute = true, localName = "WIDTH")
    var width: String = ""

    @JacksonXmlProperty(isAttribute = true, localName = "HEIGHT")
    var height: String = ""

    @JacksonXmlProperty(isAttribute = true, localName = "WC")
    var confidence: String = ""

    @JacksonXmlProperty(isAttribute = true, localName = "CONTENT")
    var content: String = ""
}