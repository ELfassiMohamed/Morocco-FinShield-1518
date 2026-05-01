# Kantara

Kantara is a fast, local-first document preprocessor designed to extract and optimize data from various file formats (PDF, XLSX, CSV) for Large Language Models (LLMs). It converts complex documents into token-efficient JSON or Markdown formats.

## Features

- **Local-First & Fast:** Runs entirely on your machine. No cloud dependencies, no data leaves your computer.
- **Embedded Web UI:** Comes with a beautiful, responsive Single Page Application (SPA) bundled inside the JAR.
- **Supported Formats:**
  - PDF (Text extraction with basic section structuring)
  - XLSX & CSV (Tabular data extraction)
- **Output Formats:**
  - **JSON:** Structured and clean, ready for programmatic consumption.
  - **Markdown:** Clean and readable, optimized for LLM prompting.
- **Token Optimization:** Automatically cleans up whitespace, collapses multiple newlines, and strips common boilerplate to save LLM tokens.

## Prerequisites

- Java 21 or higher
- Maven 3.8+

## Building

Build the single executable "fat JAR":

```bash
mvn clean package
```

## Running

Run the application:

```bash
java -jar target/kantara.jar [port]
```

By default, the server starts on port `7070` and will automatically open your default web browser to `http://localhost:7070`.

## Architecture

- **Backend:** Java 21 with Javalin 7 for a lightweight, high-performance web server utilizing Virtual Threads.
- **Frontend:** Vanilla HTML/JS/CSS.
- **Extraction:** Apache POI (Excel) and PDFBox (PDF).
