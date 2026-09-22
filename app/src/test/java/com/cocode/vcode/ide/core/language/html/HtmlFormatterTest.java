package com.cocode.vcode.ide.core.language.html;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class HtmlFormatterTest {

    private HtmlFormatter formatter;

    @Before
    public void setUp() {
        formatter = new HtmlFormatter();
        formatter.setIndentUnit("  ");
    }

    @Test
    public void testOpeningAndClosingTagsMatchingIndentation() {
        String input = "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "<meta charset=\"UTF-8\">\n" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "<title>\n" +
                "New Project\n" +
                "</title>\n" +
                "<link rel=\"stylesheet\" href=\"styles.css\">\n" +
                "</head>\n" +
                "<body>\n" +
                "<a href=\"index.html\">\n" +
                "</a>\n" +
                "<p>\n" +
                "Hello\n" +
                "</p>\n" +
                "<div>\n" +
                "Lorem ipsum dolor sit amet consectetur\n" +
                "adipiscing elit sed. do eiusmod tempor\n" +
                "incididunt ut labore et dolore. magna\n" +
                "aliqua enim ad minim.\n" +
                "</div>\n" +
                "<button type=\"button\">\n" +
                "</button>\n" +
                "<input type=\"text\">\n" +
                "<script src=\"app.js\">\n" +
                "</script>\n" +
                "</body>\n" +
                "</html>";

        String formatted = formatter.format(input);

        // Verify title opening and closing tags match indent (4 spaces)
        assertTrue(formatted.contains("    <title>\n"));
        assertTrue(formatted.contains("    </title>\n"));

        // Verify a opening and closing tags match indent (4 spaces)
        assertTrue(formatted.contains("    <a href=\"index.html\">\n    </a>"));

        // Verify button opening and closing tags match indent (4 spaces)
        assertTrue(formatted.contains("    <button type=\"button\">\n    </button>"));

        // Verify script opening and closing tags match indent (4 spaces)
        assertTrue(formatted.contains("    <script src=\"app.js\">\n    </script>"));

        // Verify p opening and closing tags match indent (4 spaces)
        assertTrue(formatted.contains("    <p>\n      Hello\n    </p>"));

        // Verify div opening and closing tags match indent (4 spaces) and multi-line text is padded
        assertTrue(formatted.contains("    <div>\n"));
        assertTrue(formatted.contains("      Lorem ipsum dolor sit amet consectetur\n"));
        assertTrue(formatted.contains("      adipiscing elit sed. do eiusmod tempor\n"));
        assertTrue(formatted.contains("    </div>\n"));

        // Verify body and head match indent (2 spaces)
        assertTrue(formatted.contains("  <head>\n"));
        assertTrue(formatted.contains("  </head>\n"));
        assertTrue(formatted.contains("  <body>\n"));
        assertTrue(formatted.contains("  </body>\n"));

        // Verify html match indent (0 spaces)
        assertTrue(formatted.contains("<html lang=\"en\">\n"));
        assertTrue(formatted.contains("</html>\n"));
    }

    @Test
    public void testEmbeddedStyleAndScript() {
        String input = "<html><head><style>body { color: red; }</style></head><body><script>console.log('hello');</script></body></html>";
        String formatted = formatter.format(input);

        assertTrue(formatted.contains("  <head>\n    <style>"));
        assertTrue(formatted.contains("    </style>\n  </head>"));
        assertTrue(formatted.contains("  <body>\n    <script>"));
        assertTrue(formatted.contains("    </script>\n  </body>"));
    }
}
