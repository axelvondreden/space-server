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

//-------------------------------------------------------
// Document Data
//-------------------------------------------------------

let selectedInvoice = null

const textTab = document.getElementById("tabText")
const tagsTab = document.getElementById("tabTags")
const invoiceTab = document.getElementById("tabInvoice")
const tabInvoiceCheck = document.getElementById("tabInvoiceCheck")
tabInvoiceCheck.addEventListener("change", async (event) => {
    if (event.target.checked) {
        await fetch(`/api/v1/import/doc/${selectedImport.id}/invoice`, {method: "post"}).then(async result => {
            if (result.ok) {
                const id = await result.text().then(id => parseInt(id))
                selectedImport.invoiceId = id
                invoiceTab.removeAttribute("disabled")
            }
        })
    } else {
        await fetch(`/api/v1/import/doc/${selectedImport.id}/invoice`, {method: "delete"}).then(async result => {
            if (result.ok) {
                selectedImport.invoiceId = null
                invoiceTab.setAttribute("disabled", "disabled")
                bootstrap.Tab.getInstance(textTab).show()
            }
        })
    }
})

let pickingDate = false
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
    datePickButton.classList.remove("active")
    if (date != null) {
        const parts = date.split(".")
        if (parts.length === 3) {
            const day = parseInt(parts[0])
            const month = parseInt(parts[1]) - 1
            const year = parseInt(parts[2])
            datePicker.datepicker("update", new Date(year, month, day))
            await setDocumentDate(new Date(year, month, day))
        }
    }
}

async function setDocumentDate(date) {
    const doc = selectedImport
    datePickButton.innerHTML = `<span class="spinner-border spinner-border-sm"></span>`
    doc.date = [date.getFullYear(), date.getMonth() + 1, date.getDate()]
    await fetch(`/api/v1/import/doc/${selectedImport.id}`, {
        method: "put",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(doc)
    }).then(async result => {
        if (result.ok) {
            selectedImport.date = doc.date
            datePickButton.innerHTML = `<i class="bi bi-crosshair"></i>`
            const sidebarEntry = document.getElementById(`${doc.guid}-date`)
            sidebarEntry.innerText = `${date.getDate()}.${date.getMonth() + 1}.${date.getFullYear()}`
            datePicker.datepicker("hide")
            bootstrap.Toast.getOrCreateInstance(savedToast).show()
        }
    })
}

const invoiceRecipientText = document.getElementById("invoiceRecipientText")
invoiceRecipientText.addEventListener("change", async () => await saveInvoiceData())

async function saveInvoiceData() {
    if (selectedInvoice == null) return
    selectedInvoice.recipient = invoiceRecipientText.value

    await fetch(`/api/v1/import/invoice/${selectedInvoice.id}`, {
        method: "put",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(selectedInvoice)
    }).then(async result => {
        if (result.ok) {
            bootstrap.Toast.getOrCreateInstance(savedToast).show()
        }
    })
}

async function fillInvoiceData() {
    await fetch(`/api/v1/import/invoice/${selectedImport.invoiceId}`).then(async result => {
        if (result.ok) {
            selectedInvoice = await result.json()
            invoiceRecipientText.value = selectedInvoice.recipient
        }
    })
}

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
    if (imp.invoiceId != null && imp.invoiceId > 0) {
        invoiceTab.removeAttribute("disabled")
        tabInvoiceCheck.checked = true
        await fillInvoiceData()
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

let addingWord = false
let addingWordPosition = {x: 0, y: 0}

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

    const selectionRect = new Konva.Rect({
        fill: "rgba(0, 0, 255, 0.2)",
        stroke: "blue",
        strokeWidth: 1,
        visible: false,
        listening: false,
    })

    const imageObj = new Image()
    imageObj.onload = () => {
        const konvaImage = new Konva.Image({
            x: 0,
            y: 0,
            image: imageObj,
            width: page.width,
            height: page.height,
        })
        konvaImage.on("dblclick", () => {
            const scale = stage.scaleX()
            const pointer = stage.getPointerPosition()
            addingWordPosition = {
                x: (pointer.x - stage.x()) / scale,
                y: (pointer.y - stage.y()) / scale
            }
            addingWord = true
        })
        konvaImage.on("click", () => {
            if (addingWord) {
                addingWord = false
                selectionRect.visible(false)
                const scale = stage.scaleX()
                const pointer = stage.getPointerPosition()
                const targetPos = {
                    x: (pointer.x - stage.x()) / scale,
                    y: (pointer.y - stage.y()) / scale
                }
                const info = {
                    x: Math.min(addingWordPosition.x, targetPos.x),
                    y: Math.min(addingWordPosition.y, targetPos.y),
                    width: Math.abs(targetPos.x - addingWordPosition.x),
                    height: Math.abs(targetPos.y - addingWordPosition.y)
                }
                showAddWordModal(info)
            }
        })
        konvaImage.on("mousemove", () => {
            if (addingWord) {
                const scale = stage.scaleX()
                const pointer = stage.getPointerPosition()
                const targetPos = {
                    x: (pointer.x - stage.x()) / scale,
                    y: (pointer.y - stage.y()) / scale
                }
                selectionRect.setAttrs({
                    visible: true,
                    x: Math.min(addingWordPosition.x, targetPos.x),
                    y: Math.min(addingWordPosition.y, targetPos.y),
                    width: Math.abs(targetPos.x - addingWordPosition.x),
                    height: Math.abs(targetPos.y - addingWordPosition.y)
                })
            }
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
    boxLayer.add(selectionRect)
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
        await fetch(`/api/v1/import/page/${selectedPage.id}/ocr/full`, {method: "post"}).then(async result => {
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
                    map.set(page.page, getImageRow(page))
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

const getImageRow = page => `
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
                            <p class="font-monospace mb-1"></p>
                        </div>
                        <img src="/api/v1/import/page/${page.id}/img?type=original&${new Date().getTime()}" class="img-fluid" alt="" onload="setImageInfo(this)">
                    </div>
                    <div class="col-2">
                        <div class="d-flex flex-column">
                            <div id="imageStatusProgress${page.page}" class="progress mt-1" role="progressbar" style="visibility: hidden">
                                <div class="progress-bar progress-bar-animated" style="width: 0"></div>
                            </div>
                            <button id="imageCleanButton${page.page}" type="button" class="btn btn-primary my-2" onclick="cleanImage(${page.page}, ${page.id})">
                                <i class="bi bi-magic"></i>&nbsp;Clean Image
                            </button>
                            
                            <label for="imageLayoutSelect${page.page}">Layout</label>
                            <select id="imageLayoutSelect${page.page}" class="form-select mb-2">
                                <option value="portrait" ${page.layout === "portrait" ? "selected" : ""}>Portrait</option>
                                <option value="landscape" ${page.layout === "landscape" ? "selected" : ""}>Landscape</option>
                            </select>
                            
                            <div class="form-check mb-1">
                                <input class="form-check-input" type="checkbox" id="imageCropCheck${page.page}" ${page.crop ? "checked" : ""}>
                                <label class="form-check-label" for="imageCropCheck${page.page}">Crop</label>
                            </div>
                            <div class="d-flex flex-column align-items-center mb-2">
                                <input type="number" class="form-control w-50" id="imageCropTop${page.page}" min="0" placeholder="Top" ${page.crop ? "value='" + page.crop.top + "'" : ""}>
                                <div class="d-flex flex-row">
                                    <input type="number" class="form-control w-50" id="imageCropLeft${page.page}" min="0" placeholder="Left" ${page.crop ? "value='" + page.crop.left + "'" : ""}>
                                    <input type="number" class="form-control w-50" id="imageCropRight${page.page}" min="0" placeholder="Right" ${page.crop ? "value='" + page.crop.right + "'" : ""}>
                                </div>
                                <input type="number" class="form-control w-50" id="imageCropBottom${page.page}" min="0" placeholder="Bottom" ${page.crop ? "value='" + page.crop.bottom + "'" : ""}>
                            </div>
                            
                            <div class="form-check">
                                <input class="form-check-input" type="checkbox" value="" id="imageGrayscaleCheck${page.page}" ${page.grayscale ? "checked" : ""}>
                                <label class="form-check-label" for="imageGrayscaleCheck${page.page}">Grayscale</label>
                            </div>
                            <div class="form-check mb-2">
                                <input class="form-check-input" type="checkbox" value="" id="imageEnhanceCheck${page.page}" ${page.enhance ? "checked" : ""}>
                                <label class="form-check-label" for="imageEnhanceCheck${page.page}">Enhance</label>
                            </div>
                            
                            <label for="imageBackgroundFilterSlider${page.page}">
                                Background Filter: <span id="imageBackgroundFilterSliderValue${page.page}">${page.backgroundFilter}</span>
                            </label>
                            <input type="range" class="form-range mb-2" id="imageBackgroundFilterSlider${page.page}"
                                min="1" max="50" step="1" value="${page.backgroundFilter}" 
                                oninput="setSliderText('imageBackgroundFilterSliderValue${page.page}', this.value)">
                                
                            <label for="imageNoiseFilterSlider${page.page}">
                                Noise Filter: <span id="imageNoiseFilterSliderValue${page.page}">${page.noiseFilter}</span>
                            </label>
                            <input type="range" class="form-range mb-2" id="imageNoiseFilterSlider${page.page}"
                                min="0" max="50" step="1" value="${page.noiseFilter}" 
                                oninput="setSliderText('imageNoiseFilterSliderValue${page.page}', this.value)">
                                
                            <div class="form-check mb-2">
                                <input class="form-check-input" type="checkbox" id="imageTextSmoothingCheck${page.page}" ${page.textSmoothing ? "checked" : ""}
                                    onchange="document.getElementById('imageTextSmoothingSlider${page.page}').disabled = !this.checked">
                                <label class="form-check-label" for="imageTextSmoothingCheck${page.page}">Text Smoothing</label>
                            </div>
                            <label for="imageTextSmoothingSlider${page.page}">
                                Smoothing Percent: <span id="imageTextSmoothingSliderValue${page.page}">${page.textSmoothing ? page.textSmoothing : 50}</span>
                            </label>
                            <input type="range" class="form-range mb-2" id="imageTextSmoothingSlider${page.page}"
                                min="0" max="100" step="1" value="${page.textSmoothing ? page.textSmoothing : 50}" 
                                oninput="setSliderText('imageTextSmoothingSliderValue${page.page}', this.value)" ${page.textSmoothing ? "" : "disabled"}>
                            
                            <div class="form-check">
                                <input class="form-check-input" type="checkbox" value="" id="imageUnrotateCheck${page.page}" ${page.unrotate ? "checked" : ""}>
                                <label class="form-check-label" for="imageUnrotateCheck${page.page}">Unrotate</label>
                            </div>
                            <div class="form-check mb-2">
                                <input class="form-check-input" type="checkbox" value="" id="imagePreserveSizeCheck${page.page}" ${page.preserveSize ? "checked" : ""}>
                                <label class="form-check-label" for="imagePreserveSizeCheck${page.page}">Preserve Size</label>
                            </div>
                            
                            <div class="form-check mb-2">
                                <input class="form-check-input" type="checkbox" value="" id="imageTrimBackgroundCheck${page.page}" ${page.trimBackground ? "checked" : ""}>
                                <label class="form-check-label" for="imageTrimBackgroundCheck${page.page}">Trim Background</label>
                            </div>
                            
                            <label for="imageBorderPaddingSlider${page.page}">
                                Border Padding: <span id="imageBorderPaddingSliderValue${page.page}">${page.borderPadding}</span>
                            </label>
                            <input type="range" class="form-range mb-2" id="imageBorderPaddingSlider${page.page}"
                                min="0" max="100" step="1" value="${page.borderPadding}" 
                                oninput="setSliderText('imageBorderPaddingSliderValue${page.page}', this.value)">
                        </div>
                    </div>
                    <div class="col">
                        <div class="d-flex align-items-center justify-content-between">
                            <p class="font-monospace mb-1"></p>
                            <h5 class="mb-1">Cleaned</h5>
                        </div>
                        <img id="cleanImage${page.page}" src="/api/v1/import/page/${page.id}/img?${new Date().getTime()}" class="img-fluid" alt="" onload="setImageInfo(this)">
                        <div id="cleanImageSpinner${page.page}" class="d-flex justify-content-center d-none">
                            <div class="spinner-border" role="status"></div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>`

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
    img.src = ""
    img.parentElement.querySelector(".font-monospace").innerHTML = ""
    const spinner = document.getElementById(`cleanImageSpinner${pageNr}`)
    spinner.classList.remove("d-none")

    await fetch(`/api/v1/import/page/${pageId}`).then(async result => {
        const page = await result.json()

        page.layout = document.getElementById(`imageLayoutSelect${pageNr}`).value
        page.crop = document.getElementById(`imageCropCheck${pageNr}`).checked ? {
            top: document.getElementById(`imageCropTop${pageNr}`).value || 0,
            left: document.getElementById(`imageCropLeft${pageNr}`).value || 0,
            right: document.getElementById(`imageCropRight${pageNr}`).value || 0,
            bottom: document.getElementById(`imageCropBottom${pageNr}`).value || 0
        } : null
        page.grayscale = document.getElementById(`imageGrayscaleCheck${pageNr}`).checked
        page.enhance = document.getElementById(`imageEnhanceCheck${pageNr}`).checked
        page.backgroundFilter = document.getElementById(`imageBackgroundFilterSlider${pageNr}`).value
        page.noiseFilter = document.getElementById(`imageNoiseFilterSlider${pageNr}`).value
        page.textSmoothing = document.getElementById(`imageTextSmoothingCheck${pageNr}`).checked ? document.getElementById(`imageTextSmoothingSlider${pageNr}`).value : null
        page.unrotate = document.getElementById(`imageUnrotateCheck${pageNr}`).checked
        page.preserveSize = document.getElementById(`imagePreserveSizeCheck${pageNr}`).checked
        page.trimBackground = document.getElementById(`imageTrimBackgroundCheck${pageNr}`).checked
        page.borderPadding = document.getElementById(`imageBorderPaddingSlider${pageNr}`).value

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
                                await fetch(`/api/v1/import/page/${pageId}/ocr/full`, {method: "post"}).then(async result => {
                                    if (result.ok) {
                                        if (pageId === selectedPage.id) {
                                            await pageSelected(pageId)
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
// Word Edit Modal
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

const wordEditDeleteButton = document.getElementById("wordEditDeleteButton")
wordEditDeleteButton.addEventListener("click", async () => {
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

const wordEditOcrButton = document.getElementById("wordEditOcrButton")
wordEditOcrButton.addEventListener("click", async () => {
    if (selectedWord != null) {
        const wordEditOcrButtonSpinner = document.getElementById("wordEditOcrButtonSpinner")
        wordEditOcrButtonSpinner.classList.remove("d-none")
        const rect = {
            x: selectedWord.x,
            y: selectedWord.y,
            width: selectedWord.width,
            height: selectedWord.height
        }
        await fetch(`/api/v1/import/page/${selectedPage.id}/ocr/part`, {
            method: "post",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(rect)
        }).then(async result => {
            if (result.ok) {
                wordEditText.value = await result.text()
                wordEditOcrButtonSpinner.classList.add("d-none")
            }
        })
    }
})

const wordModalDiv = document.getElementById("wordModal")
wordModalDiv.addEventListener("show.bs.modal", () => wordEditText.addEventListener("keyup", handleKeyPress))
wordModalDiv.addEventListener("hide.bs.modal", () => wordEditText.removeEventListener("keyup", handleKeyPress))

function handleKeyPress(event) {
    if (event.key === "Enter") {
        wordEditConfirmButton.click()
    } else if (event.key === "Delete") {
        wordEditDeleteButton.click()
    }
}


//-------------------------------------------------------
// Word Add Modal
//-------------------------------------------------------

let addWordInfo = null
const wordAddModal = new bootstrap.Modal("#wordAddModal")
const wordAddText = document.getElementById("wordAddText")
const wordAddButton = document.getElementById("wordAddButton")
wordAddButton.addEventListener("click", async () => {
    if (wordAddText.value) {
        const wordAddButtonSpinner = document.getElementById("wordAddButtonSpinner")
        wordAddButtonSpinner.classList.remove("d-none")
        addWordInfo.text = wordAddText.value
        await fetch(`/api/v1/import/page/${selectedPage.id}/word`, {
            method: "post",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(addWordInfo)
        }).then(async result => {
            if (result.ok) {
                await fetch(`/api/v1/import/page/${selectedPage.id}`).then(async result => {
                    selectedPage = await result.json()
                    wordAddButtonSpinner.classList.add("d-none")
                    wordAddModal.hide()
                    await loadImageCanvas(selectedPage)
                })
            }
        })
    }
})

function showAddWordModal(wordData) {
    wordAddModal.show()
    addWordInfo = wordData

    const wordCanvasContainer = document.getElementById("wordAddCanvasContainer")
    wordCanvasContainer.innerHTML = ""
    const containerWidth = wordCanvasContainer.offsetWidth
    const scale = Math.min(containerWidth / wordData.width, 4.0)
    wordCanvasContainer.style.height = `${(wordData.height + 20) * scale}px`
    const containerHeight = wordCanvasContainer.offsetHeight
    const stage = new Konva.Stage({
        container: "wordAddCanvasContainer",
        width: Math.min(containerWidth, wordData.width * scale),
        height: containerHeight,
        scaleX: scale,
        scaleY: scale
    })

    const layer = new Konva.Layer()
    stage.add(layer)

    const imageObj = new Image()
    imageObj.onload = () => {
        const konvaImage = new Konva.Image({
            x: -wordData.x,
            y: -wordData.y + 10,
            image: imageObj,
            width: selectedPage.width,
            height: selectedPage.height
        })
        layer.add(konvaImage)
        layer.batchDraw()
    }
    imageObj.src = `/api/v1/import/page/${selectedPage.id}/img`

    wordAddText.value = ""
    wordAddOcrButton.click()
    wordAddText.focus()
}

const wordAddOcrButton = document.getElementById("wordAddOcrButton")
wordAddOcrButton.addEventListener("click", async () => {
    if (addWordInfo != null) {
        const wordAddOcrButtonSpinner = document.getElementById("wordAddOcrButtonSpinner")
        wordAddOcrButtonSpinner.classList.remove("d-none")
        await fetch(`/api/v1/import/page/${selectedPage.id}/ocr/part`, {
            method: "post",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(addWordInfo)
        }).then(async result => {
            if (result.ok) {
                wordAddText.value = await result.text()
                wordAddOcrButtonSpinner.classList.add("d-none")
            }
        })
    }
})

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