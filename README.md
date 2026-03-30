# Kantara

**Kantara** is a Java-based command line tool that turns business documents into actionable outputs.

It reads local files such as **Excel spreadsheets**, **PDF reports**, **Word documents**, and **PowerPoint decks**, extracts structured content, sends only the required payload to a remote AI workflow, and generates polished outputs such as:

- `pptx` executive presentations
- `docx` review documents
- summaries and action reports
- searchable structured insights

The project is designed as a **local CLI + remote intelligence bridge**:

- **Local Java CLI** handles file access, extraction, sanitization, and output generation
- **n8n on Azure** orchestrates the workflow
- **Azure OpenAI** performs reasoning and synthesis
- **Azure AI Search** can be added for retrieval across document collections

## Why This Project

Many business workflows still depend on manual document work:

- reading KPI spreadsheets
- reviewing PDF reports
- preparing management presentations
- drafting Word review notes
- extracting findings from scattered files

Kantara aims to automate that workflow from the terminal in a controlled and auditable way.

Instead of uploading raw files to a generic assistant, the CLI:

1. reads files locally
2. extracts text, tables, and metadata
3. sanitizes sensitive content when needed
4. sends structured payloads to a remote AI workflow
5. generates final Office documents locally

## Core Scenario

One of the main target scenarios is:

> Read an Excel file and a PDF report, extract the useful data, create a PowerPoint presentation based on the findings, and generate a DOCX review document.

Example command:

```bash
kantara compose --data sales.xlsx --report q1-report.pdf --ppt q1-deck.pptx --review q1-review.docx
