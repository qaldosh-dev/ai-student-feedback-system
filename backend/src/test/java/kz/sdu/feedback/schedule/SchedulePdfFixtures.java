package kz.sdu.feedback.schedule;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

final class SchedulePdfFixtures {
    private SchedulePdfFixtures() { }

    static byte[] schedule() throws IOException {
        return pdf("Schedule\n2031-2032 2 term\nDay/Hour\nABC 789 Example subject (2+1+0) [3cr / 5ECTS]\n[27-Z]\nMorgan Rivera\n: Room C 200");
    }

    static byte[] pdf(String... pages) throws IOException {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            for (String text : pages) {
                var page = new PDPage();
                document.addPage(page);
                try (var content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(40, 740);
                    content.setLeading(16);
                    for (String line : text.split("\\n")) {
                        content.showText(line);
                        content.newLine();
                    }
                    content.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
