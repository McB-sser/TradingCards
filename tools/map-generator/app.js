const MAP_WIDTH = 128;
const MAP_HEIGHT = 256;
const state = { cards: [], outputDirectoryHandle: null };

const imageInput = document.querySelector("#image-input");
const cardList = document.querySelector("#card-list");
const emptyState = document.querySelector("#empty-state");
const pickOutputButton = document.querySelector("#pick-output");
const saveAllButton = document.querySelector("#save-all");
const outputStatus = document.querySelector("#output-status");
const cardTemplate = document.querySelector("#card-template");

imageInput.addEventListener("change", handleImageImport);
pickOutputButton.addEventListener("click", chooseOutputDirectory);
saveAllButton.addEventListener("click", saveAllEntries);

function handleImageImport(event) {
    for (const file of Array.from(event.target.files || [])) {
        addCardFromFile(file);
    }
    imageInput.value = "";
}

function addCardFromFile(file) {
    const image = new Image();
    const url = URL.createObjectURL(file);
    image.onload = () => {
        const id = slugify(file.name.replace(/\.[^.]+$/, ""));
        state.cards.push({
            localId: crypto.randomUUID(),
            sourceName: file.name,
            image,
            fields: {
                id,
                title: toTitle(id),
                series: "Base Set",
                number: String(state.cards.length + 1).padStart(3, "0"),
                rarity: "Common",
                artist: "",
                tags: "",
                fitMode: "cover",
                zoom: "1",
                offsetX: "0",
                offsetY: "0",
                background: "#111111",
                transparentBackground: "off",
                accent: "#d7bc6f",
                description: "",
                flavorText: ""
            }
        });
        renderCards();
        URL.revokeObjectURL(url);
    };
    image.src = url;
}

function renderCards() {
    cardList.innerHTML = "";
    emptyState.hidden = state.cards.length > 0;

    for (const card of state.cards) {
        const fragment = cardTemplate.content.cloneNode(true);
        const canvas = fragment.querySelector(".map-preview");
        const titlePreview = fragment.querySelector(".card-title-preview");
        const idPreview = fragment.querySelector(".card-id-preview");

        for (const input of fragment.querySelectorAll("[data-field]")) {
            const field = input.dataset.field;
            input.value = card.fields[field] ?? "";
            input.addEventListener("input", () => {
                card.fields[field] = input.value;
                titlePreview.textContent = card.fields.title || "(Ohne Titel)";
                idPreview.textContent = card.fields.id || "(ohne-id)";
                drawPreview(canvas, card);
            });
        }

        for (const button of fragment.querySelectorAll("[data-action]")) {
            button.addEventListener("click", () => handleCardAction(button.dataset.action, card));
        }

        titlePreview.textContent = card.fields.title || "(Ohne Titel)";
        idPreview.textContent = card.fields.id || "(ohne-id)";
        drawPreview(canvas, card);
        cardList.appendChild(fragment);
    }
}

function handleCardAction(action, card) {
    if (action === "remove") {
        state.cards = state.cards.filter((entry) => entry.localId !== card.localId);
        renderCards();
        return;
    }
    if (action === "download-png") {
        renderCardCanvas(card).toBlob((blob) => triggerDownload(blob, `${normalizeId(card.fields.id)}.png`), "image/png");
        return;
    }
    if (action === "download-json") {
        triggerDownload(new Blob([JSON.stringify(buildMetadata(card), null, 2)], { type: "application/json" }), `${normalizeId(card.fields.id)}.json`);
        return;
    }
    if (action === "save-entry") {
        saveEntry(card);
    }
}

function drawPreview(canvas, card) {
    const context = canvas.getContext("2d");
    context.clearRect(0, 0, MAP_WIDTH, MAP_HEIGHT);
    if (card.fields.transparentBackground !== "on") {
        context.fillStyle = card.fields.background;
        context.fillRect(0, 0, MAP_WIDTH, MAP_HEIGHT);
    }

    const fitMode = card.fields.fitMode;
    const zoom = Math.max(0.5, Number(card.fields.zoom) || 1);
    const offsetX = Number(card.fields.offsetX) || 0;
    const offsetY = Number(card.fields.offsetY) || 0;
    let drawWidth;
    let drawHeight;

    if (fitMode === "stretch") {
        drawWidth = MAP_WIDTH * zoom;
        drawHeight = MAP_HEIGHT * zoom;
    } else {
        const scaleX = MAP_WIDTH / card.image.width;
        const scaleY = MAP_HEIGHT / card.image.height;
        const scale = fitMode === "contain" ? Math.min(scaleX, scaleY) : Math.max(scaleX, scaleY);
        drawWidth = card.image.width * scale * zoom;
        drawHeight = card.image.height * scale * zoom;
    }

    context.drawImage(card.image, (MAP_WIDTH - drawWidth) / 2 + offsetX, (MAP_HEIGHT - drawHeight) / 2 + offsetY, drawWidth, drawHeight);

    if (card.fields.transparentBackground !== "on") {
        context.strokeStyle = card.fields.accent;
        context.lineWidth = 2;
        context.strokeRect(2, 2, MAP_WIDTH - 4, MAP_HEIGHT - 4);
    }

}

async function chooseOutputDirectory() {
    if (!("showDirectoryPicker" in window)) {
        outputStatus.textContent = "Browser unterstuetzt keinen direkten Ordnerzugriff. Nutze alternativ die Download-Buttons.";
        return;
    }
    try {
        state.outputDirectoryHandle = await window.showDirectoryPicker({ mode: "readwrite" });
        outputStatus.textContent = `Ausgabeordner: ${state.outputDirectoryHandle.name}`;
    } catch {
        outputStatus.textContent = "Ordnerauswahl abgebrochen.";
    }
}

async function saveAllEntries() {
    if (state.cards.length === 0) {
        outputStatus.textContent = "Keine Karten zum Speichern vorhanden.";
        return;
    }
    let saved = 0;
    for (const card of state.cards) {
        if (await saveEntry(card, true)) {
            saved += 1;
        }
    }
    if (saved > 0) {
        await saveManifest();
        outputStatus.textContent = `${saved} Karte(n) gespeichert. Manifest aktualisiert.`;
    }
}

async function saveEntry(card, silent = false) {
    if (!state.outputDirectoryHandle) {
        if (!silent) {
            outputStatus.textContent = "Zuerst einen Ausgabeordner waehlen.";
        }
        return false;
    }
    try {
        const id = normalizeId(card.fields.id);
        const pngHandle = await state.outputDirectoryHandle.getFileHandle(`${id}.png`, { create: true });
        const pngWritable = await pngHandle.createWritable();
        await pngWritable.write(await canvasToBlob(renderCardCanvas(card), "image/png"));
        await pngWritable.close();

        const jsonHandle = await state.outputDirectoryHandle.getFileHandle(`${id}.json`, { create: true });
        const jsonWritable = await jsonHandle.createWritable();
        await jsonWritable.write(JSON.stringify(buildMetadata(card), null, 2));
        await jsonWritable.close();

        if (!silent) {
            outputStatus.textContent = `${id}.png und ${id}.json gespeichert.`;
        }
        return true;
    } catch (error) {
        if (!silent) {
            outputStatus.textContent = `Speichern fehlgeschlagen: ${error.message}`;
        }
        return false;
    }
}

async function saveManifest() {
    if (!state.outputDirectoryHandle) {
        return;
    }
    const handle = await state.outputDirectoryHandle.getFileHandle("manifest.json", { create: true });
    const writable = await handle.createWritable();
    await writable.write(JSON.stringify({ exportedAt: new Date().toISOString(), generator: "TradingCards Map Generator", cardCount: state.cards.length, cards: state.cards.map(buildMetadata) }, null, 2));
    await writable.close();
}

function renderCardCanvas(card) {
    const canvas = document.createElement("canvas");
    canvas.width = MAP_WIDTH;
    canvas.height = MAP_HEIGHT;
    drawPreview(canvas, card);
    return canvas;
}

function buildMetadata(card) {
    const id = normalizeId(card.fields.id);
    return {
        id,
        title: card.fields.title.trim(),
        series: card.fields.series.trim(),
        number: card.fields.number.trim(),
        rarity: card.fields.rarity,
        artist: card.fields.artist.trim(),
        description: card.fields.description.trim(),
        flavorText: card.fields.flavorText.trim(),
        tags: card.fields.tags.split(",").map((tag) => tag.trim()).filter(Boolean),
        motif: {
            file: `${id}.png`,
            width: MAP_WIDTH,
            height: MAP_HEIGHT,
            layout: "1x2",
            fitMode: card.fields.fitMode,
            zoom: Number(card.fields.zoom) || 1,
            offsetX: Number(card.fields.offsetX) || 0,
            offsetY: Number(card.fields.offsetY) || 0,
            background: card.fields.background,
            transparentBackground: card.fields.transparentBackground === "on",
            accent: card.fields.accent,
            sourceFile: card.sourceName
        }
    };
}

function triggerDownload(blob, fileName) {
    if (!blob) {
        return;
    }
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = fileName;
    link.click();
    URL.revokeObjectURL(url);
}

function canvasToBlob(canvas, type) {
    return new Promise((resolve, reject) => {
        canvas.toBlob((blob) => blob ? resolve(blob) : reject(new Error("Canvas konnte nicht exportiert werden.")), type);
    });
}

function normalizeId(value) {
    const slug = slugify(value || "card");
    return slug || "card";
}

function slugify(value) {
    return value.toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "");
}

function toTitle(value) {
    return value.split(/[_\-\s]+/).filter(Boolean).map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(" ");
}
