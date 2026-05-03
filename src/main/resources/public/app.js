document.addEventListener('DOMContentLoaded', () => {
    const uploadZone = document.getElementById('upload-zone');
    const fileInput = document.getElementById('file-input');
    const processingState = document.getElementById('processing-state');
    const resultsSection = document.getElementById('results-section');
    const outputCode = document.getElementById('output-code');
    const outputRaw = document.getElementById('output-raw');
    const outputRendered = document.getElementById('output-rendered');
    const viewToggle = document.getElementById('view-toggle');
    const formatRadios = document.querySelectorAll('input[name="format"]');
    const verbosityRadios = document.querySelectorAll('input[name="verbosity"]');
    const errorToast = document.getElementById('error-toast');
    const errorMessage = document.getElementById('error-message');
    const closeErrorBtn = document.getElementById('close-error');
    
    // Result tags
    const resFilename = document.getElementById('res-filename');
    const resTokens = document.getElementById('res-tokens');
    
    // Metrics
    const metricOriginal = document.getElementById('metric-original');
    const metricProcessed = document.getElementById('metric-processed');
    const metricReduction = document.getElementById('metric-reduction');
    
    // Buttons
    const btnClear = document.getElementById('btn-clear');
    const btnCopy = document.getElementById('btn-copy');
    const btnDownload = document.getElementById('btn-download');
    const viewBtns = document.querySelectorAll('.k-view-btn');

    let currentResult = null;
    let lastUploadedFile = null;

    // --- Drag and Drop Handlers ---
    uploadZone.addEventListener('click', () => fileInput.click());

    ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
        uploadZone.addEventListener(eventName, preventDefaults, false);
    });

    function preventDefaults(e) {
        e.preventDefault();
        e.stopPropagation();
    }

    ['dragenter', 'dragover'].forEach(eventName => {
        uploadZone.addEventListener(eventName, () => uploadZone.classList.add('dragover'), false);
    });

    ['dragleave', 'drop'].forEach(eventName => {
        uploadZone.addEventListener(eventName, () => uploadZone.classList.remove('dragover'), false);
    });

    uploadZone.addEventListener('drop', handleDrop, false);
    fileInput.addEventListener('change', (e) => handleFiles(e.target.files));

    function handleDrop(e) {
        const dt = e.dataTransfer;
        const files = dt.files;
        handleFiles(files);
    }

    function handleFiles(files) {
        if (files.length === 0) return;
        const file = files[0];
        
        // 50MB check
        if (file.size > 50 * 1024 * 1024) {
            showError("File exceeds 50MB limit.");
            return;
        }

        lastUploadedFile = file;
        processFile(file);
    }

    // Handle format change after upload
    formatRadios.forEach(radio => {
        radio.addEventListener('change', () => {
            if (lastUploadedFile) {
                processFile(lastUploadedFile);
            }
        });
    });

    verbosityRadios.forEach(radio => {
        radio.addEventListener('change', () => {
            if (lastUploadedFile) {
                processFile(lastUploadedFile);
            }
        });
    });

    function processFile(file) {
        const format = document.querySelector('input[name="format"]:checked').value;
        const verbosity = document.querySelector('input[name="verbosity"]:checked').value;
        const formData = new FormData();
        formData.append('file', file);
        formData.append('format', format);
        formData.append('verbosity', verbosity);

        // UI updates
        uploadZone.classList.add('hidden');
        resultsSection.classList.add('hidden');
        processingState.classList.remove('hidden');
        errorToast.classList.add('hidden');

        fetch('/api/process', {
            method: 'POST',
            body: formData
        })
        .then(response => {
            if (!response.ok) {
                return response.json().then(err => { throw new Error(err.error || 'Server error'); });
            }
            return response.json();
        })
        .then(data => {
            displayResults(data);
        })
        .catch(err => {
            showError(err.message);
            processingState.classList.add('hidden');
            uploadZone.classList.remove('hidden');
        })
        .finally(() => {
            // Reset input so same file can be selected again
            fileInput.value = '';
        });
    }

    function displayResults(data) {
        currentResult = data;
        processingState.classList.add('hidden');
        resultsSection.classList.remove('hidden');

        // Update tags
        resFilename.textContent = data.sourceFileName;
        if (data.originalTokenEstimate && data.originalTokenEstimate > data.tokenEstimate) {
            const saved = data.originalTokenEstimate - data.tokenEstimate;
            const percent = Math.round((saved / data.originalTokenEstimate) * 100);
            resTokens.innerHTML = `<span style="text-decoration: line-through; opacity: 0.5; margin-right: 4px;">${data.originalTokenEstimate.toLocaleString()}</span> ${data.tokenEstimate.toLocaleString()} tokens`;
            
            const tooltip = resTokens.nextElementSibling;
            if (tooltip && tooltip.classList.contains('k-tooltip')) {
                tooltip.innerHTML = `<strong>Optimization: -${percent}% tokens</strong><br>Before: ${data.originalTokenEstimate.toLocaleString()}<br>After: ${data.tokenEstimate.toLocaleString()}<br><br><span style="opacity:0.8">Estimated LLM tokens based on ~4 chars/token.</span>`;
            }

            // Update metrics box
            metricOriginal.textContent = data.originalTokenEstimate.toLocaleString();
            metricProcessed.textContent = data.tokenEstimate.toLocaleString();
            metricReduction.textContent = `${percent}%`;
        } else {
            resTokens.textContent = `~${data.tokenEstimate.toLocaleString()} tokens`;
            const tooltip = resTokens.nextElementSibling;
            if (tooltip && tooltip.classList.contains('k-tooltip')) {
                tooltip.textContent = 'Estimated token count when this output is sent to an LLM (GPT-4, Claude, etc.). Based on ~4 chars/token.';
            }

            // Update metrics box
            metricOriginal.textContent = data.originalTokenEstimate ? data.originalTokenEstimate.toLocaleString() : '-';
            metricProcessed.textContent = data.tokenEstimate.toLocaleString();
            metricReduction.textContent = '0%';
        }

        // Update code block
        outputCode.className = data.format === 'json' ? 'language-json' : 'language-markdown';
        outputCode.textContent = data.output;
        
        // Handle markdown rendering
        if (data.format === 'markdown') {
            viewToggle.classList.remove('hidden');
            outputRendered.innerHTML = marked.parse(data.output);
            switchView('rendered');
        } else {
            viewToggle.classList.add('hidden');
            switchView('raw');
        }

        // Highlight syntax
        hljs.highlightElement(outputCode);
    }

    function switchView(view) {
        viewBtns.forEach(btn => {
            if (btn.dataset.view === view) {
                btn.classList.add('active');
            } else {
                btn.classList.remove('active');
            }
        });

        if (view === 'rendered') {
            outputRendered.classList.remove('hidden');
            outputRaw.classList.add('hidden');
        } else {
            outputRendered.classList.add('hidden');
            outputRaw.classList.remove('hidden');
        }
    }

    viewBtns.forEach(btn => {
        btn.addEventListener('click', () => switchView(btn.dataset.view));
    });

    function showError(msg) {
        errorMessage.textContent = msg;
        errorToast.classList.remove('hidden');
        setTimeout(() => {
            errorToast.classList.add('hidden');
        }, 5000);
    }

    closeErrorBtn.addEventListener('click', () => {
        errorToast.classList.add('hidden');
    });

    // --- Actions ---
    btnClear.addEventListener('click', () => {
        currentResult = null;
        lastUploadedFile = null;
        fileInput.value = '';
        
        resultsSection.classList.add('hidden');
        viewToggle.classList.add('hidden');
        uploadZone.classList.remove('hidden');
        
        // Reset view state
        outputRaw.classList.remove('hidden');
        outputRendered.classList.add('hidden');
        viewBtns.forEach(btn => {
            if (btn.dataset.view === 'raw') btn.classList.add('active');
            else btn.classList.remove('active');
        });
    });

    btnCopy.addEventListener('click', () => {
        if (!currentResult) return;
        navigator.clipboard.writeText(currentResult.output).then(() => {
            const originalText = btnCopy.innerHTML;
            btnCopy.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 6px;"><polyline points="20 6 9 17 4 12"></polyline></svg> Copied';
            setTimeout(() => {
                btnCopy.innerHTML = originalText;
            }, 2000);
        });
    });

    btnDownload.addEventListener('click', () => {
        if (!currentResult) return;
        
        const ext = currentResult.format === 'json' ? '.json' : '.md';
        const filename = currentResult.sourceFileName.replace(/\.[^/.]+$/, "") + "_processed" + ext;
        
        const blob = new Blob([currentResult.output], { type: 'text/plain;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    });
});
