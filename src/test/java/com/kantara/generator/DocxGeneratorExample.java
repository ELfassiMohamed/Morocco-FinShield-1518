package com.kantara.generator;

import com.kantara.ai.AiResponse;
import com.kantara.ai.Slide;

import java.util.List;

public class DocxGeneratorExample {

    public static void main(String[] args) {
        AiResponse response = new AiResponse(
                List.of(
                        "Customer churn risk increased in the enterprise segment.",
                        "Quarterly revenue growth is strongest in digital channels.",
                        "Margin pressure is mainly driven by logistics costs."
                ),
                List.of(
                        new Slide(
                                "Q2 Executive Overview",
                                List.of(
                                        "Revenue grew 8.4% year-over-year.",
                                        "Operating margin declined by 1.2 points.",
                                        "Customer acquisition improved across key regions."
                                )
                        ),
                        new Slide(
                                "Market Dynamics",
                                List.of(
                                        "Competitive pricing intensified in three core markets.",
                                        "Demand remains strong for bundled service offerings."
                                )
                        ),
                        new Slide(
                                "Strategic Recommendations",
                                List.of(
                                        "Prioritize retention programs for high-value accounts.",
                                        "Expand channel partnerships in underperforming regions.",
                                        "Improve cost controls in logistics and fulfillment."
                                )
                        )
                )
        );

        new DocxGenerator().generateReport(response, "data/business-analysis-report.docx");
    }
}
