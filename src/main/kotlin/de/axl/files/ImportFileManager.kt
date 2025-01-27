package de.axl.files

import de.axl.runCommand
import net.coobird.thumbnailator.Thumbnails
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.tools.imageio.ImageIOUtil
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.*
import javax.imageio.ImageIO
import kotlin.io.path.Path

class ImportFileManager(val dataPath: String) {

    fun createImagesFromOriginalPdf(pdfGuid: String, dpi: Int = 400): SortedMap<Int, String> {
        val document = Loader.loadPDF(getPdfOriginal(pdfGuid))
        val pages = document.numberOfPages
        val pdfRenderer = PDFRenderer(document)
        val map = sortedMapOf<Int, String>()
        for (i in 0 until pages) {
            logger.info("Creating image from original PDF page ${i + 1} / $pages")
            val guid = UUID.randomUUID().toString()
            File("$dataPath/import/pages/$guid").mkdir()
            var img = pdfRenderer.renderImageWithDPI(i, dpi.toFloat(), ImageType.RGB)
            ImageIOUtil.writeImage(img, "${dataPath}/import/pages/$guid/original.png", dpi)
            map[i + 1] = guid
        }
        document.close()
        return map
    }

    fun createDeskewedImage(guid: String, deskew: Int = 40) {
        val img = getImageOriginal(guid)
        logger.info("Creating deskewed image for ${img.name}")
        runCommand(img.parentFile, "magick ${img.name} -deskew $deskew% -trim +repage deskewed.png", logger)
    }

    fun createColorAdjustedImage(guid: String, fuzz: Int = 10) {
        val img = getImageDeskewed(guid)
        logger.info("Creating color-adjusted image for ${img.name}")
        runCommand(
            img.parentFile,
            "magick ${img.name} -fuzz $fuzz% -fill white -opaque #B6BBBF -trim +repage coloradjusted.png",
            logger
        )
    }

    fun createCroppedImage(guid: String, fuzz: Int = 20) {
        val img = getImageColorAdjusted(guid)
        logger.info("Creating cropped image for ${img.name}")
        runCommand(img.parentFile, "magick ${img.name} -fuzz $fuzz% -trim +repage final.png", logger)
    }

    fun createThumbnails(guid: String) {
        logger.info("Creating thumbnails for $guid")
        val file = getImage(guid)
        val img = ImageIO.read(file)
        if (img.width > img.height) {
            createThumbnailLandscape(guid, file, 128)
            createThumbnailLandscape(guid, file, 256)
            createThumbnailLandscape(guid, file, 512)
        } else {
            createThumbnailPortrait(guid, file, 128)
            createThumbnailPortrait(guid, file, 256)
            createThumbnailPortrait(guid, file, 512)
        }

        val square = if (img.width > img.height) img.getSubimage(0, 0, img.height, img.height) else img.getSubimage(0, 0, img.width, img.width)
        img.flush()

        createThumbnailSquare(guid, square, 128)
        createThumbnailSquare(guid, square, 256)
        createThumbnailSquare(guid, square, 512)
        square.flush()
    }

    private fun createThumbnailLandscape(guid: String, file: File, height: Int) {
        logger.info("Creating Landscape-Thumbnail $height")
        Thumbnails.of(file).height(height).outputFormat("png").toFile(File("${dataPath}/import/pages/$guid/thumb-$height.png"))
    }

    private fun createThumbnailPortrait(guid: String, file: File, width: Int) {
        logger.info("Creating Portrait-Thumbnail $width")
        Thumbnails.of(file).width(width).outputFormat("png").toFile(File("${dataPath}/import/pages/$guid/thumb-$width.png"))
    }

    private fun createThumbnailSquare(guid: String, img: BufferedImage, size: Int) {
        logger.info("Creating Thumbnail Square $size")
        Thumbnails.of(img).size(size, size).outputFormat("png").toFile(File("${dataPath}/import/pages/$guid/thumb-${size}x$size.png"))
    }

    fun getImageOriginal(guid: String) = File("$dataPath/import/pages/$guid/original.png")

    fun getImageDeskewed(guid: String) = File("$dataPath/import/pages/$guid/deskewed.png")

    fun getImageColorAdjusted(guid: String) = File("$dataPath/import/pages/$guid/coloradjusted.png")

    fun getImage(guid: String) = File("$dataPath/import/pages/$guid/final.png")

    fun getPdfOriginal(guid: String) = File("$dataPath/import/pdf/${guid}-original.pdf")

    fun getThumbnail(guid: String, size: String) = File("$dataPath/import/pages/$guid/thumb-$size.png")

    fun getUploadedFiles(): List<File> {
        return File("$dataPath/upload").listFiles().orEmpty().toList().filter { it.extension == "pdf" }
    }

    fun moveFileToImport(file: File, guid: String): File {
        val originalFilename = "$guid-original.${file.extension}"
        logger.info("Moving file to $originalFilename")
        val newPath = Files.move(file.toPath(), Path("$dataPath/import/pdf", originalFilename))
        return newPath.toFile()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.declaringClass)
    }
}