# Morocco-FinShield-1518

**A smart tool to keep Moroccan crowdfunding platforms safe and legal.**

---

###  What is this?
This project helps Moroccan startups comply with **Law 15-18** (the regulatory framework for crowdfunding). It leverages **Agentic AI** to automatically verify that investors are following legal limits and to detect fraudulent activities, such as money laundering or synthetic identities.

---

###  How it works
The system is built using a "Teamwork" approach between three core technologies:

* ** The Brain (Java / Spring Boot):** Handles the "hard" logic. It calculates investment ceilings, manages secure data persistence, and ensures the overall system architecture remains organized and scalable.
    
* ** The Eyes (Azure AI):** Utilizes **Azure AI Document Intelligence** to "read" Moroccan National ID cards (CIN) and legal documents, verifying the identity of every investor with high precision.

* ** The Assistant (n8n + AI Agents):** The orchestration layer. If the AI detects something suspicious (e.g., one bank account linked to multiple names), the assistant sends an interactive message to **Telegram**, allowing a human admin to **"Approve"** or **"Block"** the user instantly.

---

###  Key Features
* **Law 15-18 Compliance:** Automated enforcement of investment caps for retail vs. professional investors.
* **KYC Automation:** Instant extraction and validation of data from Moroccan CINs.
* **Fraud Detection:** AI-driven pattern recognition to spot "Smurfing" or identity theft.
* **Human-in-the-Loop:** Seamless integration between automated checks and manual Telegram approvals.

---

###  Tech Stack
* **Backend:** Java 21 / Spring Boot 3.4
* **Intelligence:** Azure OpenAI & Cognitive Services
* **Workflow:** n8n (Agentic Nodes)
* **Database:** PostgreSQL (with PGVector for legal RAG)

---

###  Simple Use Case
1. **User Action:** A user attempts to invest **200,000 DH** in a Tangier-based project.
2. **System Check:** **Java** calculates their current portfolio while **Azure AI** verifies their ID.
3. **Legal Validation:** The **AI Agent** compares the request against **Law 15-18** limits stored in the vector database.
4. **Outcome:** If it exceeds the limit, **n8n** triggers a notification: *"Transaction blocked: Limit exceeded for User X."*
