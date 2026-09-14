package com.walmart.kbpoc.clean;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

/**
 * Shared HTML cleanup used by both pipelines. Strips script/style/nav/header/footer
 * boilerplate and decodes entities. Produces two outputs:
 *  - plainText(): fully stripped text (what the baseline pipeline chunks/embeds)
 *  - contentHtml(): sanitized but formatting-preserving fragment of just the article
 *    body (what the enhanced pipeline feeds to the LLM segmenter and keeps as the
 *    block-level `html` field for display)
 */
public final class HtmlCleaner {

    public record Cleaned(String title, String plainText, String contentHtml) {
    }

    public static Cleaned clean(String rawHtml) {
        Document doc = Jsoup.parse(rawHtml);
        String title = doc.title();

        // Remove non-content noise before extracting anything.
        doc.select("script, style, nav, header, footer, .site-header, .site-footer, .footer-links")
                .remove();

        Element content = doc.selectFirst("main.kb-article");
        if (content == null) {
            content = doc.body();
        }

        String plainText = content.text().replaceAll("\\s+", " ").trim();

        // Sanitize but keep structural/visual tags for display (div/h1-h6/strong/em/ul/ol/li/table/a/code/p).
        Safelist safelist = Safelist.relaxed()
                .addTags("div", "section", "code")
                .addAttributes("div", "class")
                .removeAttributes("a", "target")
                .removeProtocols("a", "href", "ftp", "mailto");
        String contentHtml = Jsoup.clean(content.html(), safelist);

        return new Cleaned(title, plainText, contentHtml);
    }
}
