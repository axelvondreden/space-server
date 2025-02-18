const tooltipTriggerList = document.querySelectorAll('[data-bs-toggle="tooltip"]')
const tooltipList = [...tooltipTriggerList].map(tooltipTriggerEl => new bootstrap.Tooltip(tooltipTriggerEl))

const statusFilecount = document.getElementById("statusFilecount");
const statusProgress = document.getElementById("statusProgress");
const statusGuid = document.getElementById("statusGuid");
const statusMessage = document.getElementById("statusMessage");

const statusFilecountSpacer = document.getElementById("statusFilecountSpacer");
const statusProgressSpacer = document.getElementById("statusProgressSpacer");
const statusGuidSpacer = document.getElementById("statusGuidSpacer");

const statusIconPause = document.getElementById("statusIconPause");
const statusIconPlay = document.getElementById("statusIconPlay");
const statusIconFile = document.getElementById("statusIconFile");

const host = window.location.host;
const ws = new WebSocket("ws://" + host + "/api/v1/import/events");

ws.onopen = () => {
    statusMessage.innerHTML = "WebSocket connected";
};

ws.onclose = () => {
    statusMessage.innerHTML = "WebSocket disconnected";
};

ws.onmessage = async (event) => {
    const state = JSON.parse(event.data);
    if (state.importing) {
        statusIconPause.classList.add("d-none");
        statusIconPlay.classList.remove("d-none");
    } else {
        statusIconPause.classList.remove("d-none");
        statusIconPlay.classList.add("d-none");
    }
    if (state.currentFile != null && state.fileCount != null) {
        statusIconFile.classList.remove("d-none");
        statusFilecount.innerHTML = state.currentFile + " / " + state.fileCount;
        statusFilecountSpacer.classList.remove("d-none");
    } else {
        statusIconFile.classList.add("d-none");
        statusFilecount.innerHTML = "";
        statusFilecountSpacer.classList.add("d-none");
    }
    if (state.progress != null) {
        statusProgress.classList.remove("d-none");
        statusProgressSpacer.classList.remove("d-none");
        const percent = (state.progress * 100).toFixed(2) + "%";
        statusProgress.firstElementChild.style.width = percent;
        statusProgress.firstElementChild.innerHTML = percent;
    } else {
        statusProgress.classList.add("d-none");
        statusProgressSpacer.classList.add("d-none");
        statusProgress.firstElementChild.style.width = "0%";
        statusProgress.firstElementChild.innerHTML = "0%";
    }
    if (state.guid) {
        const hashIcon = "<i class='bi bi-hash'></i>";
        statusGuid.innerHTML = hashIcon + state.guid.substring(0, 4) + "..." + state.guid.substring(state.guid.length - 4);
        statusGuid.setAttribute("data-bs-original-title", state.guid);
        statusGuidSpacer.classList.remove("d-none");
    } else {
        statusGuid.innerHTML = "";
        statusGuidSpacer.classList.add("d-none");
    }
    if (state.message) {
        statusMessage.innerHTML = state.message;
    } else {
        statusMessage.innerHTML = "";
    }
    if (state.completedDocId != null) {
        await fetch("/api/v1/import/doc/" + state.completedDocId).then(async result => {
            if (result.ok) {
                const imp = await result.json();
                addImportToSidebar(imp);
            }
        }).catch((error) => console.log("Something went wrong. " + error));
    }
};

const inputFile = document.getElementById("formFileMultiple");
const modalDiv = document.getElementById("uploadModal");
const fileList = document.getElementById("fileList");

modalDiv.addEventListener("shown.bs.modal", () => {
    inputFile.value = "";
});

inputFile.addEventListener("change", () => {
    fileList.innerHTML = "";
    for (const file of inputFile.files) {
        const li = document.createElement("li");
        li.innerHTML = file.name;
        li.id = file.name;
        li.classList.add("list-group-item");
        fileList.appendChild(li);
    }
});


const uploadButton = document.getElementById("uploadButton");
const uploadModal = new bootstrap.Modal("#uploadModal");

uploadButton.addEventListener("click", async () => {
    for (const file of inputFile.files) {
        const formData = new FormData();
        formData.append("file", file);
        await fetch("/api/v1/upload", {
            method: "post",
            body: formData,
        }).then(result => {
            if (result.ok) {
                const li = document.getElementById(file.name);
                li.classList.add("list-group-item-success");
            }
        }).catch((error) => console.log("Something went wrong. " + error));
    }
    uploadModal.hide();
    await fetch("/api/v1/upload/collect", {method: "post"});
});

window.addEventListener("load", async () => {
    Split(['#importDocImageArea', '#importDocDataArea'], {
        onDragEnd: async () => {
            await loadImageCanvas(selectedPage, true);
        }
    });
    await fillSidebar();
});


const sidebar = document.getElementById("sidebar");

async function fillSidebar() {
    sidebar.innerHTML = "";
    await fetch("/api/v1/import/doc").then(async result => {
        if (result.ok) {
            const imports = await result.json();
            imports.forEach(imp => addImportToSidebar(imp));
        }
    });
}

function addImportToSidebar(imp) {
    const date = (!imp.date) ? "" : imp.date[2] + "." + imp.date[1] + "." + imp.date[0];
    const a = document.createElement("a");
    const page1 = imp.pages.find(p => p.page === 1);
    a.href = "#";
    a.className = "list-group-item list-group-item-action py-3 lh-sm";
    a.innerHTML = `
            <div class="d-flex w-100 align-items-center justify-content-between">
                <strong class="mb-1">${date}</strong>
                <span class="text-body-secondary"><i class="bi bi-file-earmark-text"></i> ${imp.pages.length}</span>
            </div>
            <div class="d-flex w-100">
                <img id="${imp.guid}-thumb" src="/api/v1/import/page/${page1.id}/thumb" class="img-fluid" alt="Thumbnail">
            </div>
        `;
    a.addEventListener("click", () => {
        docSelected(imp);
    });

    sidebar.appendChild(a);
}

const importDocArea = document.getElementById("importDocArea");
const importNoDocSelected = document.getElementById("importNoDocSelected");
const importGuid = document.getElementById("importGuid");
const languageSelect = document.getElementById("languageSelect");
const pageText = document.getElementById("pageText");

const sliderDeskew = document.getElementById("sliderDeskew");
const sliderDeskewValue = document.getElementById("sliderDeskewValue");
const deskewImageSpinner = document.getElementById("deskewImageSpinner");

const sliderColorFuzz = document.getElementById("sliderColorFuzz");
const sliderColorFuzzValue = document.getElementById("sliderColorFuzzValue");
const colorImageSpinner = document.getElementById("colorImageSpinner");

const sliderCropFuzz = document.getElementById("sliderCropFuzz");
const sliderCropFuzzValue = document.getElementById("sliderCropFuzzValue");
const cropImageSpinner = document.getElementById("cropImageSpinner");

const editImageOriginal = document.getElementById("editImageOriginal");
const editImageDeskewed = document.getElementById("editImageDeskewed");
const editImageColorAdjusted = document.getElementById("editImageColorAdjusted");
const editImageCropped = document.getElementById("editImageCropped");

const imageEditorConfirmButton = document.getElementById("imageEditConfirmButton");
const imageEditorCloseButton = document.getElementById("imageEditCloseButton");

const imageModal = new bootstrap.Modal("#imageModal");

const pagination = document.getElementById("pagination");

let selectedImport = null;
let selectedPage = null;

async function docSelected(imp) {
    selectedImport = imp;
    pageText.value = "";
    languageSelect.value = "";
    let dateElem = $("#importDate");
    dateElem.datepicker({
        format: "dd.mm.yyyy",
    });
    dateElem.datepicker("update", null);
    if (imp == null) {
        selectedPage = null;
        importNoDocSelected.classList.remove("d-none");
        importDocArea.classList.add("d-none");
        return;
    }
    if (imp.date) {
        dateElem.datepicker("update", new Date(imp.date[0], imp.date[1] - 1, imp.date[2]));
    }
    const page1 = imp.pages.find(p => p.page === 1);
    await pageSelected(page1.id);

    pagination.innerHTML = "";
    imp.pages.forEach(page => {
        const li = document.createElement("li");
        if (page.page === 1) {
            li.className = "page-item active";
        } else {
            li.className = "page-item";
        }
        li.innerHTML = "<a class='page-link' href='#' onclick='pageSelected(" + page.id + ")'>" + page.page + "</a>";
        pagination.appendChild(li);
    })
}

async function pageSelected(pageId) {
    await fetch("/api/v1/import/page/" + pageId).then(async result => {
        if (result.ok) {
            const page = await result.json();
            selectedPage = page;
            importNoDocSelected.classList.add("d-none");
            importDocArea.classList.remove("d-none");

            importGuid.innerHTML = selectedImport.guid;

            languageSelect.value = selectedImport.ocrLanguage;

            await loadImageCanvas(page, true)

            sliderDeskew.value = page.deskew;
            sliderDeskewValue.innerText = page.deskew;
            sliderColorFuzz.value = page.colorFuzz;
            sliderColorFuzzValue.innerText = page.colorFuzz;
            sliderCropFuzz.value = page.cropFuzz;
            sliderCropFuzzValue.innerText = page.cropFuzz;

            pagination.childNodes.forEach(node => {
                if (node.className === "page-item active") {
                    node.className = "page-item";
                }
                if (node.children[0].innerText == page.page) {
                    node.className = "page-item active";
                }
            })

            editImageOriginal.src = "/api/v1/import/page/" + page.id + "/img?type=original";
            editImageDeskewed.src = "/api/v1/import/page/" + page.id + "/img?type=deskewed";
            editImageColorAdjusted.src = "/api/v1/import/page/" + page.id + "/img?type=color";
            editImageCropped.src = "/api/v1/import/page/" + page.id + "/img";

            await fetch("/api/v1/import/page/" + page.id + "/text").then(async result => {
                pageText.value = await result.text();
            });

            imageEditorChanged(false);
        }
    });
}

const canvasContainer = document.getElementById("canvasContainer");
const showBlocksCheck = document.getElementById("showBlocksCheck");
showBlocksCheck.checked = (document.cookie.indexOf("showBlocks=1") >= 0);
const showLinesCheck = document.getElementById("showLinesCheck");
showLinesCheck.checked = (document.cookie.indexOf("showLines=1") >= 0);
const showWordsCheck = document.getElementById("showWordsCheck");
showWordsCheck.checked = (document.cookie.indexOf("showWords=1") >= 0);

let currentBlocks = [];
let currentLines = [];
let currentWords = [];

let canvasPosition = {x: 0, y: 0};
let canvasScale = 1;

async function loadImageCanvas(page, reset = false) {
    canvasContainer.innerHTML = "";
    currentBlocks = [];
    currentLines = [];
    currentWords = [];
    if (reset) {
        canvasPosition = {x: 0, y: 0};
    }
    const containerWidth = canvasContainer.offsetWidth;
    const containerHeight = canvasContainer.offsetHeight;
    const stage = new Konva.Stage({
        container: 'canvasContainer',
        width: containerWidth,
        height: containerHeight,
        position: canvasPosition,
        draggable: true
    });

    stage.on('wheel', (e) => {
        e.evt.preventDefault();
        let direction = e.evt.deltaY > 0 ? 1 : -1;
        let scale = stage.scaleX();
        const pointer = stage.getPointerPosition();
        const mousePointTo = {
            x: (pointer.x - stage.x()) / scale,
            y: (pointer.y - stage.y()) / scale
        };
        if (direction < 0) {
            scale = Math.min(scale + 0.1, 4);
        } else {
            scale = Math.max(scale - 0.1, 0.1);
        }
        stage.scale({x: scale, y: scale});
        const newPos = {
            x: pointer.x - mousePointTo.x * scale,
            y: pointer.y - mousePointTo.y * scale,
        };
        stage.position(newPos);
        canvasPosition = newPos;
        canvasScale = scale;
    });

    stage.on('dragmove', (e) => {
        canvasPosition = e.target.position();
    })

    const layer = new Konva.Layer();
    stage.add(layer);

    const imageObj = new Image();
    imageObj.onload = () => {
        const konvaImage = new Konva.Image({
            x: 0,
            y: 0,
            image: imageObj,
            width: page.width,
            height: page.height,
        });
        if (reset) {
            canvasScale = containerWidth / page.width;
        }
        stage.scale({x: canvasScale, y: canvasScale});
        layer.add(konvaImage);
        layer.batchDraw();
    };
    imageObj.src = "/api/v1/import/page/" + page.id + "/img";

    const boxLayer = new Konva.Layer();
    stage.add(boxLayer);
    const textLayer = new Konva.Layer();
    stage.add(textLayer);

    await fetch("/api/v1/import/page/" + page.id + "/blocks").then(async result => {
        if (result.ok) {
            const blocks = await result.json();
            blocks.forEach(block => {
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
                });
                boxLayer.add(blockBox);
                currentBlocks.push(blockBox);

                block.lines.forEach(line => {
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
                    });
                    boxLayer.add(lineBox);
                    currentLines.push(lineBox);

                    line.words.forEach(word => {
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
                        });
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
                        });
                        wordBox.on("mousemove", () => {
                            wordBox.strokeWidth(4);
                            wordBox.fill("white");
                            wordText.show();
                        });
                        wordBox.on("mouseleave", () => {
                            wordBox.strokeWidth(2);
                            wordBox.fill("transparent");
                            wordText.hide();
                        });
                        wordBox.on("click", () => {
                            showWordModal(word);
                        });
                        boxLayer.add(wordBox);
                        textLayer.add(wordText);
                        currentWords.push(wordBox);
                    });
                });

            });
            boxLayer.batchDraw();
        }
    });
}

const refreshTextButton = document.getElementById("refreshTextButton");
const refreshTextSpinner = document.getElementById("refreshTextSpinner");

refreshTextButton.addEventListener("click", async () => {
    if (selectedImport != null && selectedPage != null) {
        refreshTextSpinner.classList.remove("d-none");
        pageText.value = "";
        await fetch("/api/v1/import/page/" + selectedPage.id + "/edit/ocr", {method: "post"}).then(async result => {
            if (result.ok) {
                const ocr = await result.json();
                pageText.value = ocr.text;
                await loadImageCanvas(selectedPage)
                refreshTextSpinner.classList.add("d-none");
            }
        });
    }
});

showBlocksCheck.addEventListener("change", async (event) => {
    if (event.target.checked) {
        currentBlocks.forEach(block => block.show());
        document.cookie = "showBlocks=1; path=/; max-age=31536000";
    } else {
        currentBlocks.forEach(block => block.hide());
        document.cookie = "showBlocks=0; path=/; max-age=31536000";
    }
});

showLinesCheck.addEventListener("change", async (event) => {
    if (event.target.checked) {
        currentLines.forEach(line => line.show());
        document.cookie = "showLines=1; path=/; max-age=31536000";
    } else {
        currentLines.forEach(line => line.hide());
        document.cookie = "showLines=0; path=/; max-age=31536000";
    }
});

showWordsCheck.addEventListener("change", async (event) => {
    if (event.target.checked) {
        currentWords.forEach(word => word.show());
        document.cookie = "showWords=1; path=/; max-age=31536000";
    } else {
        currentWords.forEach(word => word.hide());
        document.cookie = "showWords=0; path=/; max-age=31536000";
    }
});

const deleteButton = document.getElementById("deleteButton");
const deleteButtonSpinner = document.getElementById("deleteButtonSpinner");

deleteButton.addEventListener("click", async () => {
    if (selectedImport != null) {
        deleteButtonSpinner.classList.remove("d-none");
        await fetch("/api/v1/import/doc/" + selectedImport.id, {method: "delete"}).then(async result => {
            if (result.ok) {
                await docSelected(null);
                await fillSidebar();
                deleteButtonSpinner.classList.add("d-none");
            }
        });
    }
});

sliderDeskew.addEventListener("input", async () => sliderDeskewValue.innerText = sliderDeskew.value);
sliderColorFuzz.addEventListener("input", async () => sliderColorFuzzValue.innerText = sliderColorFuzz.value);
sliderCropFuzz.addEventListener("input", async () => sliderCropFuzzValue.innerText = sliderCropFuzz.value);

sliderDeskew.addEventListener("change", async () => {
    if (selectedImport != null && selectedPage != null) {
        imageEditorChanged(true);
        await deskewImg(selectedPage, sliderDeskew.value, sliderColorFuzz.value, sliderCropFuzz.value);
    }
});

sliderColorFuzz.addEventListener("change", async () => {
    if (selectedImport != null && selectedPage != null) {
        imageEditorChanged(true);
        await colorImg(selectedPage, sliderColorFuzz.value, sliderCropFuzz.value);
    }
});

sliderCropFuzz.addEventListener("change", async () => {
    if (selectedImport != null && selectedPage != null) {
        imageEditorChanged(true);
        await cropImg(selectedPage, sliderCropFuzz.value);
    }
});

imageEditorConfirmButton.addEventListener("click", async () => {
    if (selectedImport != null && selectedPage != null) {
        await fetch("/api/v1/import/page/" + selectedPage.id + "/edit/thumbs", {method: "post"}).then(async result => {
            if (result.ok) {
                const img = document.getElementById(selectedImport.guid + "-thumb");
                img.src = "/api/v1/import/page/" + selectedPage.id + "/thumb?" + new Date().getTime();
                imageModal.hide();
            }
        });
    }
});

function imageEditorChanged(changed) {
    if (changed) {
        imageEditorConfirmButton.classList.remove("d-none");
        imageEditorCloseButton.classList.add("d-none");
    } else {
        imageEditorConfirmButton.classList.add("d-none");
        imageEditorCloseButton.classList.remove("d-none");
    }
}

async function deskewImg(page, deskew, color, crop) {
    editImageDeskewed.src = "";
    deskewImageSpinner.classList.remove("d-none");
    await fetch("/api/v1/import/page/" + page.id + "/edit/deskew?deskew=" + deskew, {method: "post"}).then(async result => {
        if (result.ok) {
            deskewImageSpinner.classList.add("d-none");
            editImageDeskewed.src = "/api/v1/import/page/" + page.id + "/img?type=deskewed&" + new Date().getTime();
            await colorImg(page, color, crop);
        }
    });
}

async function colorImg(page, color, crop) {
    editImageColorAdjusted.src = "";
    colorImageSpinner.classList.remove("d-none");
    await fetch("/api/v1/import/page/" + page.id + "/edit/color?fuzz=" + color, {method: "post"}).then(async result => {
        if (result.ok) {
            colorImageSpinner.classList.add("d-none");
            editImageColorAdjusted.src = "/api/v1/import/page/" + page.id + "/img?type=color&" + new Date().getTime();
            await cropImg(page, crop);
        }
    });
}

async function cropImg(page, crop) {
    editImageCropped.src = "";
    cropImageSpinner.classList.remove("d-none");
    await fetch("/api/v1/import/page/" + page.id + "/edit/crop?fuzz=" + crop, {method: "post"}).then(async result => {
        if (result.ok) {
            cropImageSpinner.classList.add("d-none");
            editImageCropped.src = "/api/v1/import/page/" + page.id + "/img?" + new Date().getTime();
            await pageSelected(page.id);
        }
    });
}

const wordModal = new bootstrap.Modal("#wordModal");
const wordModalDiv = document.getElementById("wordModal");
const wordEditText = document.getElementById("wordEditText");
const wordEditConfirmButton = document.getElementById("wordEditConfirmButton");
const wordEditConfirmButtonSpinner = document.getElementById("wordEditConfirmButtonSpinner");
const wordEditDeleteButton = document.getElementById("wordEditDeleteButton");
const wordEditDeleteButtonSpinner = document.getElementById("wordEditDeleteButtonSpinner");
let selectedWord = null;

wordEditConfirmButton.addEventListener("click", async () => {
    if (selectedWord != null && wordEditText.value) {
        wordEditConfirmButtonSpinner.classList.remove("d-none");
        await fetch("/api/v1/import/word/" + selectedWord.id + "/text", {method: "post", body: wordEditText.value}).then(async result => {
            if (result.ok) {
                await fetch("/api/v1/import/page/" + selectedPage.id).then(async result => {
                    selectedPage = await result.json();
                    await fetch("/api/v1/import/page/" + selectedPage.id + "/text").then(async result => {
                        pageText.value = await result.text();
                        wordEditConfirmButtonSpinner.classList.add("d-none");
                        wordModal.hide();
                        await loadImageCanvas(selectedPage);
                    });
                });
            }
        });
    }
});

wordEditDeleteButton.addEventListener("click", async () => {
    if (selectedWord != null) {
        wordEditDeleteButtonSpinner.classList.remove("d-none");
        await fetch("/api/v1/import/word/" + selectedWord.id, {method: "delete"}).then(async result => {
            if (result.ok) {
                await fetch("/api/v1/import/page/" + selectedPage.id).then(async result => {
                    selectedPage = await result.json();
                    await fetch("/api/v1/import/page/" + selectedPage.id + "/text").then(async result => {
                        pageText.value = await result.text();
                        wordEditDeleteButtonSpinner.classList.add("d-none");
                        wordModal.hide();
                        await loadImageCanvas(selectedPage);
                    });
                });
            }
        });
    }
});

function showWordModal(word) {
    selectedWord = word;
    wordEditText.value = word.text;
    wordModal.show();
    wordEditText.focus();
}

wordModalDiv.addEventListener('show.bs.modal', () => {
    wordEditText.addEventListener("keyup", handleEnterPress);
});

wordModalDiv.addEventListener('hide.bs.modal', () => {
    wordEditText.removeEventListener("keyup", handleEnterPress);
});

function handleEnterPress(event) {
    if (event.key === "Enter") {
        wordEditConfirmButton.click();
    }
}