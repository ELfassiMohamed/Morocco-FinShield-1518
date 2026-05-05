# Kantara

Kantara is a fast, local-first document preprocessor designed to extract and optimize data from various file formats (PDF, XLSX, CSV) for Large Language Models (LLMs). It converts complex documents into token-efficient JSON or Markdown formats.

## Features

- **Local-First & Fast:** Runs entirely on your machine. No cloud dependencies, no data leaves your computer.
- **Embedded Web UI:** Comes with a beautiful, responsive Single Page Application (SPA) bundled inside the JAR.
- **Supported Formats:**
  - PDF (page-aware text extraction, metadata, section detection, and table-like block detection)
  - XLSX & CSV (multi-sheet tables, headers, row numbers, delimiter detection, and formatted cell values)
- **Output Formats:**
  - **JSON:** Structured and clean, ready for programmatic consumption.
  - **Markdown:** Clean and readable, optimized for LLM prompting.
- **Token Optimization:** Automatically cleans up whitespace, collapses multiple newlines, and strips common boilerplate to save LLM tokens.
- **LLM Chunking:** Adds source-aware chunks with token estimates for direct use in retrieval and prompting workflows.
- **Batch Processing:** Upload one document or a batch of PDF/XLSX/CSV files in the embedded web UI.

## Prerequisites

- Java 21 or higher
- Maven 3.8+

## Downloads

Release builds are published from Git tags named like `v1.0.0`.

By default, the server starts on port `7070` and will automatically open your default web browser to `http://localhost:7070`.

## Architecture

- **Backend:** Java 21 with Javalin 7 for a lightweight, high-performance web server utilizing Virtual Threads.
- **Frontend:** Vanilla HTML/JS/CSS.
- **Extraction:** Apache POI (Excel) and PDFBox (PDF).
