package de.axl.files

import de.axl.importing.events.ImportStateEvent
import de.axl.runCommand
import de.axl.serialization.api.ExposedImportPage
import kotlinx.coroutines.flow.MutableSharedFlow
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

    suspend fun createImagesFromOriginalPdf(pdfGuid: String, importFlow: MutableSharedFlow<ImportStateEvent>, state: ImportStateEvent, step: Double): SortedMap<Int, String> {
        val document = Loader.loadPDF(getPdfOriginal(pdfGuid))
        val pages = document.numberOfPages
        val pdfRenderer = PDFRenderer(document)
        val map = sortedMapOf<Int, String>()
        for (i in 0 until pages) {
            logger.info("Creating image from PDF page ${i + 1} / $pages")
            importFlow.emit(state.copy(progress = state.progress?.plus((step / pages) * i), message = "Creating image from PDF page ${i + 1} / $pages"))
            val guid = UUID.randomUUID().toString()
            File("$dataPath/import/pages/$guid").mkdir()
            var img = pdfRenderer.renderImageWithDPI(i, 400F, ImageType.RGB)
            ImageIOUtil.writeImage(img, "${dataPath}/import/pages/$guid/original.png", 400)
            map[i + 1] = guid
        }
        document.close()
        return map
    }

    fun createCleanedImage(page: ExposedImportPage) {
        val img = getImageOriginal(page.guid)
        logger.info("Creating cleaned image for ${img.name}")

        val crop = if (page.crop != null) "-c ${page.crop.left},${page.crop.top},${page.crop.right},${page.crop.bottom} " else ""
        val grayscale = if (page.grayscale) "-g " else ""
        val enhance = if (page.enhance) "stretch" else "none"
        val unrotate = if (page.unrotate) "-u " else ""
        val preserveSize = if (page.preserveSize) "-P " else ""
        val smoothing = if (page.textSmoothing != null) "-t ${page.textSmoothing} " else ""
        val trimBackground = if (page.trimBackground) "-T " else ""
        val args = "-l ${page.layout} $crop$grayscale-e $enhance -f ${page.backgroundFilter} -o ${page.noiseFilter} $unrotate$preserveSize$smoothing$trimBackground-p ${page.borderPadding}"
        runCommand(img.parentFile, "./../../../../script/textcleaner $args ${img.name} cleaned.png", logger)
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

    fun getImage(guid: String) = File("$dataPath/import/pages/$guid/cleaned.png")

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