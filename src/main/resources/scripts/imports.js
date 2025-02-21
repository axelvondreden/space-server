//-------------------------------------------------------
// WebSocket
//-------------------------------------------------------

const statusFilecount = document.getElementById("statusFilecount")
const statusProgress = document.getElementById("statusProgress")
const statusGuid = document.getElementById("statusGuid")
const statusMessage = document.getElementById("statusMessage")

const statusFilecountSpacer = document.getElementById("statusFilecountSpacer")
const statusProgressSpacer = document.getElementById("statusProgressSpacer")
const statusGuidSpacer = document.getElementById("statusGuidSpacer")

const statusIconPause = document.getElementById("statusIconPause")
const statusIconPlay = document.getElementById("statusIconPlay")
const statusIconFile = document.getElementById("statusIconFile")

const ws = new WebSocket(`ws://${window.location.host}/api/v1/import/events`)

ws.onopen = () => {
    statusMessage.innerHTML = "WebSocket connected"
}

ws.onclose = () => {
    statusMessage.innerHTML = "WebSocket disconnected"
}

ws.onmessage = async (event) => {
    const state = JSON.parse(event.data)
    if (state.importing) {
        statusIconPause.classList.add("d-none")
        statusIconPlay.classList.remove("d-none")
    } else {
        statusIconPause.classList.remove("d-none")
        statusIconPlay.classList.add("d-none")
    }
    if (state.currentFile != null && state.fileCount != null) {
        statusIconFile.classList.remove("d-none")
        statusFilecount.innerHTML = `${state.currentFile} / ${state.fileCount}`
        statusFilecountSpacer.classList.remove("d-none")
    } else {
        statusIconFile.classList.add("d-none")
        statusFilecount.innerHTML = ""
        statusFilecountSpacer.classList.add("d-none")
    }
    if (state.progress != null) {
        statusProgress.classList.remove("d-none")
        statusProgressSpacer.classList.remove("d-none")
        const percent = `${(state.progress * 100).toFixed(2)}%`
        statusProgress.firstElementChild.style.width = percent
        statusProgress.firstElementChild.innerHTML = percent
    } else {
        statusProgress.classList.add("d-none")
        statusProgressSpacer.classList.add("d-none")
        statusProgress.firstElementChild.style.width = "0%"
        statusProgress.firstElementChild.innerHTML = "0%"
    }
    if (state.guid) {
        statusGuid.innerHTML = `<i class="bi bi-hash"></i>${state.guid.substring(0, 4)}...${state.guid.substring(state.guid.length - 4)}`
        statusGuid.setAttribute("data-bs-original-title", state.guid)
        statusGuidSpacer.classList.remove("d-none")
    } else {
        statusGuid.innerHTML = ""
        statusGuidSpacer.classList.add("d-none")
    }
    if (state.message) {
        statusMessage.innerHTML = state.message
    } else {
        statusMessage.innerHTML = ""
    }
    if (state.completedDocId != null) {
        await fetch(`/api/v1/import/doc/${state.completedDocId}`).then(async result => {
            if (result.ok) {
                const imp = await result.json()
                addImportToSidebar(imp)
            }
        }).catch((error) => console.log(`Something went wrong. ${error}`))
    }
}

//-------------------------------------------------------
// Sidebar
//-------------------------------------------------------

window.addEventListener("load", async () => {
    // noinspection JSUnusedGlobalSymbols
    Split(['#importDocImageArea', '#importDocDataArea'], {
        onDragEnd: async () => {
            await loadImageCanvas(selectedPage, true)
        }
    })
    await fillSidebar()
})

const sidebar = document.getElementById("sidebar")

async function fillSidebar() {
    sidebar.innerHTML = ""
    await fetch("/api/v1/import/doc").then(async result => {
        if (result.ok) {
            const imports = await result.json()
            imports.forEach(imp => addImportToSidebar(imp))
        }
    })
}

function addImportToSidebar(imp) {
    const date = (!imp.date) ? "" : `${imp.date[2]}.${imp.date[1]}.${imp.date[0]}`
    const a = document.createElement("a")
    const page1 = imp.pages.find(p => p.page === 1)
    a.href = "#"
    a.className = "list-group-item list-group-item-action py-3 lh-sm"
    a.innerHTML = `
        <div class="d-flex w-100 align-items-center justify-content-between">
            <strong id="${imp.guid}-date" class="mb-1">${date}</strong>
            <span class="text-body-secondary"><i class="bi bi-file-earmark-text"></i> ${imp.pages.length}</span>
        </div>
        <div class="d-flex w-100">
            <img id="${imp.guid}-thumb" src="/api/v1/import/page/${page1.id}/thumb" class="img-fluid" alt="Thumbnail">
        </div>`
    a.addEventListener("click", async () => await docSelected(imp))

    sidebar.appendChild(a)
}

//-------------------------------------------------------
// Document Area
//-------------------------------------------------------

let selectedImport = null
let selectedPage = null

const languageSelect = document.getElementById("languageSelect")
languageSelect.addEventListener("change", async (event) => {
    const doc = selectedImport
    doc.language = event.target.value
    await fetch(`/api/v1/import/doc/${selectedImport.id}`, {
        method: "put",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(doc)
    }).then(async result => {
        if (result.ok) {
            bootstrap.Toast.getOrCreateInstance(savedToast).show()
        }
    })
})

const datePicker = $("#importDate")
datePicker.datepicker({format: "dd.mm.yyyy"})
datePicker.datepicker().on("changeDate", async function (e) {
    await setDocumentDate(e.date)
})

const savedToast = document.getElementById("savedToast")

const pagination = document.getElementById("pagination")

const textTab = document.getElementById("tabText")
const tagsTab = document.getElementById("tabTags")
const invoiceTab = document.getElementById("tabInvoice")
const tabInvoiceCheck = document.getElementById("tabInvoiceCheck")
tabInvoiceCheck.addEventListener("change", async (event) => {
    const doc = selectedImport
    doc.isInvoice = event.target.checked
    await fetch(`/api/v1/import/doc/${selectedImport.id}`, {
        method: "put",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(doc)
    }).then(async result => {
        if (result.ok) {
            selectedImport.isInvoice = event.target.checked
            if (selectedImport.isInvoice) {
                invoiceTab.removeAttribute("disabled")
            } else {
                invoiceTab.setAttribute("disabled", "disabled")
                bootstrap.Tab.getInstance(textTab).show()
            }
            bootstrap.Toast.getOrCreateInstance(savedToast).show()
        }
    })
})

async function docSelected(imp) {
    const importDocArea = document.getElementById("importDocArea")
    const importNoDocSelected = document.getElementById("importNoDocSelected")
    selectedImport = imp
    datePicker.datepicker("update", null)
    importNoDocSelected.classList.add("d-none")
    importDocArea.classList.remove("d-none")
    if (imp == null) {
        selectedPage = null
        importNoDocSelected.classList.remove("d-none")
        importDocArea.classList.add("d-none")
        return
    }
    if (imp.date) {
        datePicker.datepicker("update", new Date(imp.date[0], imp.date[1] - 1, imp.date[2]))
    }

    languageSelect.value = selectedImport.language
    document.getElementById("importGuid").innerHTML = selectedImport.guid

    await pageSelected(imp.pages.find(p => p.page === 1).id)

    bootstrap.Tab.getInstance(textTab).show()
    if (imp.isInvoice) {
        invoiceTab.removeAttribute("disabled")
        tabInvoiceCheck.checked = true
    } else {
        invoiceTab.setAttribute("disabled", "disabled")
        tabInvoiceCheck.checked = false
    }

    pagination.innerHTML = ""
    imp.pages.forEach(page => {
        const li = document.createElement("li")
        li.className = page.page === 1 ? "page-item active" : "page-item";
        li.innerHTML = `<a class="page-link" href="#" onclick="pageSelected(${page.id})">${page.page}</a>`
        pagination.appendChild(li)
    })
}

async function pageSelected(pageId) {
    await fetch(`/api/v1/import/page/${pageId}`).then(async result => {
        if (result.ok) {
            const page = await result.json()
            selectedPage = page

            await loadImageCanvas(page, true)

            pagination.childNodes.forEach(node => {
                if (node.className === "page-item active") {
                    node.className = "page-item"
                }
                // noinspection EqualityComparisonWithCoercionJS
                if (node.children[0].innerText == page.page) {
                    node.className = "page-item active"
                }
            })
        }
    })
}

let pickingDate = false
const datePickButtonIcon = document.getElementById("datePickButtonIcon")
const datePickButtonSpinner = document.getElementById("datePickButtonSpinner")
const datePickButton = document.getElementById("datePickButton")
datePickButton.addEventListener("click", async () => {
    if (pickingDate) {
        await datePicked(null)
    } else {
        pickingDate = true
        datePickButton.classList.add("active")
    }
})

async function datePicked(date) {
    pickingDate = false
    if (date != null) {
        const parts = date.split(".")
        if (parts.length === 3) {
            const day = parseInt(parts[0])
            const month = parseInt(parts[1]) - 1
            const year = parseInt(parts[2])
            datePicker.datepicker("update", new Date(year, month, day))
            datePickButton.classList.remove("active")
            await setDocumentDate(new Date(year, month, day))
        }
    }
}

async function setDocumentDate(date) {
    const doc = selectedImport
    datePickButtonSpinner.classList.remove("d-none")
    datePickButtonIcon.classList.add("d-none")
    doc.date = [date.getFullYear(), date.getMonth() + 1, date.getDate()]
    await fetch(`/api/v1/import/doc/${selectedImport.id}`, {
        method: "put",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(doc)
    }).then(async result => {
        if (result.ok) {
            selectedImport.date = doc.date
            datePickButtonSpinner.classList.add("d-none")
            datePickButtonIcon.classList.remove("d-none")
            const sidebarEntry = document.getElementById(`${doc.guid}-date`)
            sidebarEntry.innerText = `${date.getDate()}.${date.getMonth() + 1}.${date.getFullYear()}`
            datePicker.datepicker("hide")
            bootstrap.Toast.getOrCreateInstance(savedToast).show()
        }
    })
}

//-------------------------------------------------------
// Canvas
//-------------------------------------------------------

let currentBlocks = []
let currentLines = []
let currentWords = []

const showBlocksCheck = document.getElementById("showBlocksCheck")
showBlocksCheck.checked = (document.cookie.indexOf("showBlocks=1") >= 0)
showBlocksCheck.addEventListener("change", async (event) => {
    if (event.target.checked) {
        currentBlocks.forEach(block => block.show())
        document.cookie = "showBlocks=1; path=/; max-age=31536000"
    } else {
        currentBlocks.forEach(block => block.hide())
        document.cookie = "showBlocks=0; path=/; max-age=31536000"
    }
})

const showLinesCheck = document.getElementById("showLinesCheck")
showLinesCheck.checked = (document.cookie.indexOf("showLines=1") >= 0)
showLinesCheck.addEventListener("change", async (event) => {
    if (event.target.checked) {
        currentLines.forEach(line => line.show())
        document.cookie = "showLines=1; path=/; max-age=31536000"
    } else {
        currentLines.forEach(line => line.hide())
        document.cookie = "showLines=0; path=/; max-age=31536000"
    }
})

const showWordsCheck = document.getElementById("showWordsCheck")
showWordsCheck.checked = (document.cookie.indexOf("showWords=1") >= 0)
showWordsCheck.addEventListener("change", async (event) => {
    if (event.target.checked) {
        currentWords.forEach(word => word.show())
        document.cookie = "showWords=1; path=/; max-age=31536000"
    } else {
        currentWords.forEach(word => word.hide())
        document.cookie = "showWords=0; path=/; max-age=31536000"
    }
})

const showWordConfidenceCheck = document.getElementById("showWordConfidenceCheck")
showWordConfidenceCheck.checked = (document.cookie.indexOf("showWordConfidence=1") >= 0)
showWordConfidenceCheck.addEventListener("change", async (event) => {
    const wordSpans = document.querySelectorAll("[data-confidence]")
    if (event.target.checked) {
        wordSpans.forEach(wordSpan => wordSpan.style.backgroundColor = getConfidenceColor(wordSpan.dataset.confidence))
        document.cookie = "showWordConfidence=1; path=/; max-age=31536000"
    } else {
        wordSpans.forEach(wordSpan => wordSpan.style.backgroundColor = "transparent")
        document.cookie = "showWordConfidence=0; path=/; max-age=31536000"
    }
})

let canvasPosition = {x: 0, y: 0}
let canvasScale = 1

let containerWidth = 0
let containerHeight = 0

let stage = null

const pageText = document.getElementById("pageText")

async function loadImageCanvas(page, reset = false) {
    const canvasContainer = document.getElementById("canvasContainer")
    canvasContainer.innerHTML = ""
    pageText.innerHTML = ""
    currentBlocks = []
    currentLines = []
    currentWords = []
    if (reset) {
        canvasPosition = {x: 0, y: 0}
    }
    containerWidth = canvasContainer.offsetWidth
    containerHeight = canvasContainer.offsetHeight
    stage = new Konva.Stage({
        container: "canvasContainer",
        width: containerWidth,
        height: containerHeight,
        position: canvasPosition,
        draggable: true
    })

    stage.on("wheel", (e) => {
        e.evt.preventDefault()
        let direction = e.evt.deltaY > 0 ? 1 : -1
        let scale = stage.scaleX()
        const pointer = stage.getPointerPosition()
        const mousePointTo = {
            x: (pointer.x - stage.x()) / scale,
            y: (pointer.y - stage.y()) / scale
        }
        if (direction < 0) {
            scale = Math.min(scale + 0.1, 4)
        } else {
            scale = Math.max(scale - 0.1, 0.1)
        }
        stage.scale({x: scale, y: scale})
        const newPos = {
            x: pointer.x - mousePointTo.x * scale,
            y: pointer.y - mousePointTo.y * scale,
        }
        stage.position(newPos)
        canvasPosition = newPos
        canvasScale = scale
    })

    stage.on("dragmove", (e) => {
        canvasPosition = e.target.position()
    })

    const layer = new Konva.Layer()
    stage.add(layer)

    const imageObj = new Image()
    imageObj.onload = () => {
        const konvaImage = new Konva.Image({
            x: 0,
            y: 0,
            image: imageObj,
            width: page.width,
            height: page.height,
        })
        if (reset) {
            canvasScale = containerWidth / page.width
        }
        stage.scale({x: canvasScale, y: canvasScale})
        layer.add(konvaImage)
        layer.batchDraw()
    }
    imageObj.src = `/api/v1/import/page/${page.id}/img${reset ? `?${new Date().getTime()}` : ""}`

    const boxLayer = new Konva.Layer()
    stage.add(boxLayer)
    const textLayer = new Konva.Layer()
    stage.add(textLayer)

    const wordMarker = new Konva.Ellipse({
        stroke: "red",
        strokeWidth: 12,
        visible: false
    })
    boxLayer.add(wordMarker)

    await fetch(`/api/v1/import/page/${page.id}/blocks`).then(async result => {
        if (result.ok) {
            const blocks = await result.json()
            blocks.sort((a, b) => a.y - b.y).forEach(block => {
                const blockBox = new Konva.Rect({
                    x: block.x,
                    y: block.y,
                    width: block.width,
                    height: block.height,
                    fill: "transparent",
                    stroke: "black",
                    strokeWidth: 2,
                    name: "rect",
                    visible: showBlocksCheck.checked
                })
                boxLayer.add(blockBox)
                currentBlocks.push(blockBox)

                block.lines.sort((a, b) => a.y - b.y).forEach(line => {
                    const lineBox = new Konva.Rect({
                        x: line.x,
                        y: line.y,
                        width: line.width,
                        height: line.height,
                        fill: "transparent",
                        stroke: "black",
                        strokeWidth: 2,
                        name: "rect",
                        visible: showLinesCheck.checked
                    })
                    boxLayer.add(lineBox)
                    currentLines.push(lineBox)

                    const lineDiv = document.createElement("div")
                    pageText.appendChild(lineDiv)

                    line.words.sort((a, b) => a.x - b.x).forEach(word => {
                        const wordSpan = document.createElement("span")
                        wordSpan.innerText = word.text
                        wordSpan.dataset.confidence = word.ocrConfidence
                        if (showWordConfidenceCheck.checked) {
                            wordSpan.style.backgroundColor = getConfidenceColor(word.ocrConfidence)
                        }
                        lineDiv.appendChild(wordSpan)
                        lineDiv.appendChild(document.createTextNode(" "))

                        const wordBox = new Konva.Rect({
                            x: word.x,
                            y: word.y,
                            width: word.width,
                            height: word.height,
                            fill: "transparent",
                            stroke: "black",
                            strokeWidth: 2,
                            name: "rect",
                            visible: showWordsCheck.checked
                        })
                        const wordText = new Konva.Text({
                            x: word.x,
                            y: word.y,
                            width: word.width,
                            height: word.height,
                            text: word.text,
                            fontFamily: "Calibri",
                            fontSize: word.height,
                            padding: 0,
                            textFill: "white",
                            fill: "black",
                            alpha: 0.75,
                            align: "center",
                            verticalAlign: "middle",
                            visible: false,
                            listening: false
                        })
                        wordBox.on("mouseenter", () => {
                            wordBox.strokeWidth(4)
                            wordBox.fill("white")
                            wordText.show()
                            wordSpan.style.outline = "2px solid white"
                        })
                        wordBox.on("mouseleave", () => {
                            wordBox.strokeWidth(2)
                            wordBox.fill("transparent")
                            wordText.hide()
                            wordSpan.style.outline = "none"
                        })
                        wordBox.on("click", () => {
                            if (pickingDate) {
                                datePicked(word.text)
                            } else {
                                showWordModal(word)
                            }
                        })

                        wordSpan.addEventListener("click", () => {
                            if (pickingDate) {
                                datePicked(word.text)
                            } else {
                                showWordModal(word)
                            }
                        })
                        wordSpan.addEventListener("mouseenter", () => {
                            wordBox.strokeWidth(4)
                            wordSpan.style.outline = "2px solid white"
                            wordMarker.position({x: word.x + word.width / 2, y: word.y + word.height / 2})
                            wordMarker.radiusX(word.width)
                            wordMarker.radiusY(word.height)
                            wordMarker.visible(true)
                        })
                        wordSpan.addEventListener("mouseleave", () => {
                            wordBox.strokeWidth(2)
                            wordSpan.style.outline = "none"
                            wordMarker.visible(false)
                        })

                        boxLayer.add(wordBox)
                        textLayer.add(wordText)
                        currentWords.push(wordBox)
                    })
                })
            })
            boxLayer.batchDraw()
        }
    })
}

function getConfidenceColor(conf) {
    return conf > 0.88 ? "rgba(0, 128, 0, 0.6)" : (conf > 0.65 ? "rgba(255, 165, 0, 0.6)" : "rgba(255, 0, 0, 0.6)")
}

const alignHorizontalButton = document.getElementById("alignHorizontalButton")
alignHorizontalButton.addEventListener("click", () => {
    canvasPosition = {x: 0, y: 0}
    canvasScale = containerWidth / selectedPage.width
    stage.position(canvasPosition)
    stage.scale({x: canvasScale, y: canvasScale})
})

const alignVerticalButton = document.getElementById("alignVerticalButton")
alignVerticalButton.addEventListener("click", () => {
    canvasScale = containerHeight / selectedPage.height
    if (selectedPage.width * canvasScale < containerWidth) {
        canvasPosition = {x: (containerWidth - (selectedPage.width * canvasScale)) / 2, y: 0}
    } else {
        canvasPosition = {x: 0, y: 0}
    }
    stage.position(canvasPosition)
    stage.scale({x: canvasScale, y: canvasScale})
})

//-------------------------------------------------------
// Document Data
//-------------------------------------------------------

const refreshTextSpinner = document.getElementById("refreshTextSpinner")
const refreshTextButton = document.getElementById("refreshTextButton")
refreshTextButton.addEventListener("click", async () => {
    if (selectedImport != null && selectedPage != null) {
        refreshTextSpinner.classList.remove("d-none")
        pageText.innerHTML = ""
        await fetch(`/api/v1/import/page/${selectedPage.id}/edit/ocr`, {method: "post"}).then(async result => {
            if (result.ok) {
                await loadImageCanvas(selectedPage)
                refreshTextSpinner.classList.add("d-none")
            }
        })
    }
})

const deleteButtonSpinner = document.getElementById("deleteButtonSpinner")
const deleteButton = document.getElementById("deleteButton")
deleteButton.addEventListener("click", async () => {
    if (selectedImport != null) {
        deleteButtonSpinner.classList.remove("d-none")
        await fetch(`/api/v1/import/doc/${selectedImport.id}`, {method: "delete"}).then(async result => {
            if (result.ok) {
                await docSelected(null)
                await fillSidebar()
                deleteButtonSpinner.classList.add("d-none")
            }
        })
    }
})

//-------------------------------------------------------
// Image Modal
//-------------------------------------------------------

document.getElementById("imageModal").addEventListener("show.bs.modal", () => {
    if (selectedImport != null) {
        const imageModalBody = document.getElementById("imageModalBody")
        imageModalBody.innerHTML = ""
        const map = new Map()
        selectedImport.pages.sort((a, b) => a.page - b.page).map(page => page.id).forEach(async id => {
            await fetch(`/api/v1/import/page/${id}`).then(async result => {
                if (result.ok) {
                    const page = await result.json()
                    const row = `
                        <div class="accordion-item">
                            <h2 class="accordion-header">
                                <button class="accordion-button ${page.page === 1 ? "" : "collapsed"}" type="button" data-bs-toggle="collapse" data-bs-target="#imageCollapse${page.page}">
                                Page #${page.page}
                                </button>
                            </h2>
                            <div id="imageCollapse${page.page}" class="accordion-collapse collapse ${page.page === 1 ? "show" : ""}" data-bs-parent="#imageModalBody">
                                <div class="accordion-body">
                                    <div class="row">
                                        <div class="col">
                                            <div class="d-flex align-items-center justify-content-between">
                                                <h5 class="mb-1">Original</h5>
                                                <p class="font-monospace mb-1">This is in monospace</p>
                                            </div>
                                            <img src="/api/v1/import/page/${id}/img?type=original&${new Date().getTime()}" class="img-fluid" alt="" onload="setImageInfo(this)">
                                        </div>
                                        <div class="col-2">
                                            <div class="d-flex flex-column">
                                                <div id="imageStatusProgress${page.page}" class="progress mt-1" role="progressbar" style="visibility: hidden">
                                                    <div class="progress-bar progress-bar-animated" style="width: 0"></div>
                                                </div>
                                                <button id="imageCleanButton${page.page}" type="button" class="btn btn-primary my-2" onclick="cleanImage(${page.page}, ${page.id})">
                                                    <i class="bi bi-magic"></i>&nbsp;Clean Image
                                                </button>
                                                <label for="imageLayoutSelect${page.page}" class="form-label">Layout</label>
                                                <select id="imageLayoutSelect" class="form-select">
                                                    <option value="portrait" ${page.layout === "portrait" ? "selected" : ""}>Portrait</option>
                                                    <option value="landscape" ${page.layout === "landscape" ? "selected" : ""}>Landscape</option>
                                                </select>
                                                
                                                <!--<label for="imageLayoutSelect${page.page}" class="form-label">Crop: <span id="sliderCleanFuzzValue${page.page}">${page.cropFuzz}</span></label>
                                                <input 
                                                    type="range" 
                                                    class="form-range" 
                                                    id="sliderCleanFuzz${page.page}" 
                                                    min="0" 
                                                    max="100" 
                                                    step="1" 
                                                    value="${page.cropFuzz}" 
                                                    oninput="setSliderText('sliderCleanFuzzValue${page.page}', this.value)"
                                                    onchange="cleanImage(${page.page}, ${page.id})">-->
                                            </div>
                                        </div>
                                        <div class="col">
                                            <div class="d-flex align-items-center justify-content-between">
                                                <p class="font-monospace mb-1">This is in monospace</p>
                                                <h5 class="mb-1">Cleaned</h5>
                                            </div>
                                            <img id="cleanImage${page.page}" src="/api/v1/import/page/${id}/img?${new Date().getTime()}" class="img-fluid" alt="" onload="setImageInfo(this)">
                                            <div id="cleanImageSpinner${page.page}" class="d-flex justify-content-center d-none">
                                                <div class="spinner-border" role="status"></div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>`
                    map.set(page.page, row)
                    if (map.size === selectedImport.pages.length) {
                        map.entries().toArray().sort((a, b) => a[0] - b[0]).forEach(entry => {
                            imageModalBody.innerHTML += entry[1]
                        })
                    }
                }
            })
        })
    }
})

async function setImageInfo(img) {
    const imageInfo = img.parentElement.querySelector(".font-monospace")
    await fetch(img.src).then(async result => {
        if (result.ok) {
            const fileSize = await result.blob().then(blob => blob.size / 1024 / 1024)
            imageInfo.innerHTML = `${img.naturalWidth}x${img.naturalHeight} | ${fileSize.toFixed(2)}MB`
        }
    })
}

function setSliderText(spanId, value) {
    const span = document.getElementById(spanId)
    span.innerText = value
}

async function cleanImage(pageNr, pageId) {
    const imageCleanButton = document.getElementById(`imageCleanButton${pageNr}`)
    imageCleanButton.disabled = true
    const imageStatusProgress = document.getElementById(`imageStatusProgress${pageNr}`)
    imageStatusProgress.style.visibility = "visible"
    imageStatusProgress.firstElementChild.style.width = "50%"
    imageStatusProgress.firstElementChild.innerHTML = "Cleaning Image..."
    const img = document.getElementById(`cleanImage${pageNr}`)
    const spinner = document.getElementById(`cleanImageSpinner${pageNr}`)
    //const crop = document.getElementById(`sliderCropFuzz${pageNr}`).value
    img.src = ""
    spinner.classList.remove("d-none")

    await fetch(`/api/v1/import/page/${pageId}`).then(async result => {
        const page = await result.json()

        /*
        val layout: String = ImportPageDbService.Orientation.PORTRAIT.name.lowercase(),
        val crop: ImportPageCrop? = null,
        val grayscale: Boolean = false,
        val enhance: Boolean = true,
        val backgroundFilter: Int = 15,
        val noiseFilter: Int = 5,
        val unrotate: Boolean = true,
        val preserveSize: Boolean = false,
        val textSmoothing: Int? = null,
        val trimBackground: Boolean = true,
        val borderPadding: Int = 0,
         */
        await fetch(`/api/v1/import/page/${pageId}`, {
            method: "put",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(page)
        }).then(async result => {
            if (result.ok) {
                bootstrap.Toast.getOrCreateInstance(savedToast).show()
                await fetch(`/api/v1/import/page/${pageId}/edit/clean`, {method: "post"}).then(async result => {
                    if (result.ok) {
                        spinner.classList.add("d-none")
                        img.src = `/api/v1/import/page/${pageId}/img?${new Date().getTime()}`

                        imageStatusProgress.firstElementChild.style.width = "70%"
                        imageStatusProgress.firstElementChild.innerHTML = "Creating Thumbnails..."
                        await fetch(`/api/v1/import/page/${pageId}/edit/thumbs`, {method: "post"}).then(async result => {
                            if (result.ok) {
                                if (pageNr === 1) {
                                    const img = document.getElementById(selectedImport.guid + "-thumb")
                                    img.src = `/api/v1/import/page/${pageId}/thumb?${new Date().getTime()}`
                                }

                                imageStatusProgress.firstElementChild.style.width = "90%"
                                imageStatusProgress.firstElementChild.innerHTML = "Running OCR..."
                                await fetch(`/api/v1/import/page/${pageId}/edit/ocr`, {method: "post"}).then(async result => {
                                    if (result.ok) {
                                        if (pageId === selectedPage.id) {
                                            await loadImageCanvas(page)
                                        }
                                        imageCleanButton.disabled = false
                                        imageStatusProgress.style.visibility = "hidden"
                                    }
                                })
                            }
                        })
                    }
                })
            }
        })
    })
}

//-------------------------------------------------------
// Word Modal
//-------------------------------------------------------

const wordModal = new bootstrap.Modal("#wordModal")
const wordEditText = document.getElementById("wordEditText")
let selectedWord = null

const wordEditConfirmButton = document.getElementById("wordEditConfirmButton")
wordEditConfirmButton.addEventListener("click", async () => {
    if (selectedWord != null && wordEditText.value) {
        const wordEditConfirmButtonSpinner = document.getElementById("wordEditConfirmButtonSpinner")
        wordEditConfirmButtonSpinner.classList.remove("d-none")
        await fetch(`/api/v1/import/word/${selectedWord.id}/text`, {method: "post", body: wordEditText.value}).then(async result => {
            if (result.ok) {
                await fetch(`/api/v1/import/page/${selectedPage.id}`).then(async result => {
                    selectedPage = await result.json()
                    wordEditConfirmButtonSpinner.classList.add("d-none")
                    wordModal.hide()
                    await loadImageCanvas(selectedPage)
                })
            }
        })
    }
})

document.getElementById("wordEditDeleteButton").addEventListener("click", async () => {
    if (selectedWord != null) {
        const wordEditDeleteButtonSpinner = document.getElementById("wordEditDeleteButtonSpinner")
        wordEditDeleteButtonSpinner.classList.remove("d-none")
        await fetch(`/api/v1/import/word/${selectedWord.id}`, {method: "delete"}).then(async result => {
            if (result.ok) {
                await fetch(`/api/v1/import/page/${selectedPage.id}`).then(async result => {
                    selectedPage = await result.json()
                    wordEditDeleteButtonSpinner.classList.add("d-none")
                    wordModal.hide()
                    await loadImageCanvas(selectedPage)
                })
            }
        })
    }
})

const suggestionsCollapse = document.getElementById("suggestionsCollapse")
const suggestionsButton = document.getElementById("suggestionsButton")
suggestionsButton.addEventListener("click", () => {
    if (!suggestionsCollapse.classList.contains("d-none")) {
        suggestionsCollapse.classList.add("d-none")
        suggestionsButton.innerHTML = `<span class="badge text-bg-secondary">${selectedWord.spellingSuggestions.length}</span> Show Suggestions`
    } else {
        suggestionsCollapse.classList.remove("d-none")
        suggestionsButton.innerHTML = `<span class="badge text-bg-secondary">${selectedWord.spellingSuggestions.length}</span> Hide Suggestions`
    }
})

function showWordModal(word) {
    selectedWord = word
    wordEditText.value = word.text
    document.getElementById("wordEditConfidenceText").innerText = `Confidence: ${word.ocrConfidence * 100}%`
    wordModal.show()

    const wordCanvasContainer = document.getElementById("wordCanvasContainer")
    wordCanvasContainer.innerHTML = ""
    const containerWidth = wordCanvasContainer.offsetWidth
    const scale = Math.min(containerWidth / word.width, 4.0)
    wordCanvasContainer.style.height = `${(word.height + 20) * scale}px`
    const containerHeight = wordCanvasContainer.offsetHeight
    const stage = new Konva.Stage({
        container: "wordCanvasContainer",
        width: Math.min(containerWidth, word.width * scale),
        height: containerHeight,
        scaleX: scale,
        scaleY: scale
    })

    const layer = new Konva.Layer()
    stage.add(layer)

    const imageObj = new Image()
    imageObj.onload = () => {
        const konvaImage = new Konva.Image({
            x: -word.x,
            y: -word.y + 10,
            image: imageObj,
            width: selectedPage.width,
            height: selectedPage.height
        })
        layer.add(konvaImage)
        layer.batchDraw()
    }
    imageObj.src = `/api/v1/import/page/${selectedPage.id}/img`

    if (suggestionsCollapse.classList.contains("d-none")) {
        suggestionsButton.innerHTML = `<span class="badge text-bg-secondary">${word.spellingSuggestions.length}</span> Show Suggestions`
    } else {
        suggestionsButton.innerHTML = `<span class="badge text-bg-secondary">${word.spellingSuggestions.length}</span> Hide Suggestions`
    }

    suggestionsCollapse.innerHTML = ""

    if (word.spellingSuggestions.length > 0) {
        suggestionsButton.removeAttribute("disabled")
    } else {
        suggestionsButton.setAttribute("disabled", "disabled")
    }

    word.spellingSuggestions.forEach(suggestion => {
        const div = document.createElement("div")
        div.classList.add("col-sm-auto")
        const button = document.createElement("button")
        button.type = "button"
        button.classList.add("btn", "btn-outline-secondary", "btn-sm", "m-1")
        button.innerText = suggestion.suggestion
        button.addEventListener("click", () => {
            wordEditText.value = suggestion.suggestion
        })
        div.appendChild(button)
        suggestionsCollapse.appendChild(div)
    })

    wordEditText.focus()
}

const wordModalDiv = document.getElementById("wordModal")
wordModalDiv.addEventListener("show.bs.modal", () => wordEditText.addEventListener("keyup", handleEnterPress))
wordModalDiv.addEventListener("hide.bs.modal", () => wordEditText.removeEventListener("keyup", handleEnterPress))

function handleEnterPress(event) {
    if (event.key === "Enter") {
        wordEditConfirmButton.click()
    }
}

//-------------------------------------------------------
// Upload Modal
//-------------------------------------------------------

const inputFile = document.getElementById("formFileMultiple")

document.getElementById("uploadModal").addEventListener("shown.bs.modal", () => inputFile.value = "")

inputFile.addEventListener("change", () => {
    const fileList = document.getElementById("fileList")
    fileList.innerHTML = ""
    for (const file of inputFile.files) {
        const li = document.createElement("li")
        li.innerHTML = file.name
        li.id = file.name
        li.classList.add("list-group-item")
        fileList.appendChild(li)
    }
})

const uploadModal = new bootstrap.Modal("#uploadModal")

document.getElementById("uploadButton").addEventListener("click", async () => {
    for (const file of inputFile.files) {
        const formData = new FormData()
        formData.append("file", file)
        await fetch("/api/v1/upload", {method: "post", body: formData}).then(result => {
            if (result.ok) {
                const li = document.getElementById(file.name)
                li.classList.add("list-group-item-success")
            }
        }).catch((error) => console.log(`Something went wrong. ${error}`))
    }
    uploadModal.hide()
    await fetch("/api/v1/upload/collect", {method: "post"})
})