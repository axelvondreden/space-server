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
import javax.imageio.ImageIO
import kotlin.io.path.Path

class FileManagerImport(val dataPath: String) {

    fun createImagesFromOriginalPdf(guid: String, dpi: Float = 400F) {
        val document = Loader.loadPDF(getPdfOriginal(guid))
        val pages = document.numberOfPages
        val pdfRenderer = PDFRenderer(document)
        for (i in 0 until pages) {
            logger.info("Creating image from original PDF page ${i + 1} / $pages")
            var img = pdfRenderer.renderImageWithDPI(i, dpi, ImageType.RGB)
            ImageIOUtil.writeImage(img, "${dataPath}/import/${guid}-original-${(i + 1).toString().padStart(4, '0')}.png", 400)
        }
        document.close()
    }

    fun createDeskewedImage(guid: String, page: Int, deskew: Int = 40) {
        val img = getImageOriginal(guid, page)
        logger.info("Creating deskewed image for ${img.name}")
        runCommand(img.parentFile, "magick ${img.name} -deskew $deskew% -trim +repage ${guid}-deskewed-${page.toString().padStart(4, '0')}.png", logger)
    }

    fun createColorAdjustedImage(guid: String, page: Int, fuzz: Int = 10) {
        val img = getImageDeskewed(guid, page)
        logger.info("Creating color-adjusted image for ${img.name}")
        runCommand(
            img.parentFile,
            "magick ${img.name} -fuzz $fuzz% -fill white -opaque #B6BBBF -trim +repage ${guid}-coloradjusted-${page.toString().padStart(4, '0')}.png",
            logger
        )
    }

    fun createCroppedImage(guid: String, page: Int, fuzz: Int = 20) {
        val img = getImageColorAdjusted(guid, page)
        logger.info("Creating cropped image for ${img.name}")
        runCommand(img.parentFile, "magick ${img.name} -fuzz $fuzz% -trim +repage ${guid}-${page.toString().padStart(4, '0')}.png", logger)
    }

    fun createThumbnails(guid: String, page: Int) {
        logger.info("Creating thumbnails for $guid page $page")
        val file = getImage(guid, page)
        val img = ImageIO.read(file)
        if (img.width > img.height) {
            createThumbnailLandscape(file, 128)
            createThumbnailLandscape(file, 256)
            createThumbnailLandscape(file, 512)
        } else {
            createThumbnailPortrait(file, 128)
            createThumbnailPortrait(file, 256)
            createThumbnailPortrait(file, 512)
        }

        val square = if (img.width > img.height) img.getSubimage(0, 0, img.height, img.height) else img.getSubimage(0, 0, img.width, img.width)
        img.flush()

        createThumbnailSquare(square, file.nameWithoutExtension, 128)
        createThumbnailSquare(square, file.nameWithoutExtension, 256)
        createThumbnailSquare(square, file.nameWithoutExtension, 512)
        square.flush()
    }

    private fun createThumbnailLandscape(file: File, height: Int) {
        logger.info("Creating Landscape-Thumbnail $height")
        Thumbnails.of(file).height(height).outputFormat("png").toFile(File("${dataPath}/import/${file.nameWithoutExtension}-$height.png"))
    }

    private fun createThumbnailPortrait(file: File, width: Int) {
        logger.info("Creating Portrait-Thumbnail $width")
        Thumbnails.of(file).width(width).outputFormat("png").toFile(File("${dataPath}/import/${file.nameWithoutExtension}-$width.png"))
    }

    private fun createThumbnailSquare(img: BufferedImage, name: String, size: Int) {
        logger.info("Creating Thumbnail Square $size")
        Thumbnails.of(img).size(size, size).outputFormat("png").toFile(File("${dataPath}/import/$name-${size}x$size.png"))
    }

    fun getImagesOriginal(guid: String): List<File> =
        File("$dataPath/import")
            .listFiles { file -> file.name.startsWith("$guid-original") && file.extension == "png" }
            .orEmpty().toList()
            .sortedBy { it.nameWithoutExtension.substringAfterLast("-").toInt() }

    fun getImageOriginal(guid: String, page: Int) = File("$dataPath/import/${guid}-original-${page.toString().padStart(4, '0')}.png")

    fun getImageDeskewed(guid: String, page: Int) = File("$dataPath/import/${guid}-deskewed-${page.toString().padStart(4, '0')}.png")

    fun getImageColorAdjusted(guid: String, page: Int) = File("$dataPath/import/${guid}-coloradjusted-${page.toString().padStart(4, '0')}.png")

    fun getImages(guid: String): List<File> =
        File("$dataPath/import")
            .listFiles { file -> file.name.startsWith("$guid-") && imageTypes.none { it in file.name } && file.extension == "png" }
            .orEmpty().toList()
            .sortedBy { it.nameWithoutExtension.substringAfterLast("-").toInt() }

    fun getImage(guid: String, page: Int) = File("$dataPath/import/${guid}-${page.toString().padStart(4, '0')}.png")

    fun getPdfOriginal(guid: String) = File("$dataPath/import/${guid}-original.pdf")

    fun getThumbnail(guid: String, page: Int, size: String) = File("$dataPath/import/${guid}-${page.toString().padStart(4, '0')}-$size.png")

    fun getUploadedFiles(): List<File> {
        return File("$dataPath/upload").listFiles().orEmpty().toList().filter { it.extension == "pdf" }
    }

    fun moveFileToImport(file: File, guid: String): File {
        val originalFilename = "$guid-original.${file.extension}"
        logger.info("Moving file to $originalFilename")
        val newPath = Files.move(file.toPath(), Path("$dataPath/import", originalFilename))
        return newPath.toFile()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.declaringClass)
        private val imageTypes = listOf("-original-", "-deskewed-", "-coloradjusted-")
    }
}