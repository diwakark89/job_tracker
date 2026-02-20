package com.thewalkersoft.linkedin_job_tracker.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

object JobScraper {
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"

    data class JobInfo(
        val companyName: String,
        val jobTitle: String,
        val description: String
    )

    suspend fun scrapeJobInfo(url: String): JobInfo = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(10000)
                .get()

            // Extract company name
            val companyName = doc.select("a.topcard__org-name-link").text().takeIf { it.isNotBlank() }
                ?: doc.select(".topcard__flavor").text().takeIf { it.isNotBlank() }
                ?: doc.select("span.topcard__flavor--bullet").text().takeIf { it.isNotBlank() }
                ?: doc.select("a[data-tracking-control-name=public_jobs_topcard-org-name]").text().takeIf { it.isNotBlank() }
                ?: "Unknown Company"

            // Extract job title
            val jobTitle = doc.select("h1.topcard__title").text().takeIf { it.isNotBlank() }
                ?: doc.select("h2.topcard__title").text().takeIf { it.isNotBlank() }
                ?: doc.select(".top-card-layout__title").text().takeIf { it.isNotBlank() }
                ?: doc.select("h1[class*=top-card]").text().takeIf { it.isNotBlank() }
                ?: "Unknown Position"

            // Extract description (preserving line breaks)
            val description = extractTextWithLineBreaks(doc.select(".description__text")).takeIf { it.isNotBlank() }
                ?: extractTextWithLineBreaks(doc.select(".show-more-less-html__markup")).takeIf { it.isNotBlank() }
                ?: extractTextWithLineBreaks(doc.select("div[class*=description]")).takeIf { it.isNotBlank() }
                ?: doc.select("meta[name=description]").attr("content").takeIf { it.isNotBlank() }
                ?: "Unable to scrape job description from the provided URL."

            JobInfo(
                companyName = companyName.trim(),
                jobTitle = jobTitle.trim(),
                description = description
            )
        } catch (e: Exception) {
            JobInfo(
                companyName = "Unknown Company",
                jobTitle = "Unknown Position",
                description = "Error scraping job: ${e.message}"
            )
        }
    }

    suspend fun scrapeJobDescription(url: String): String = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(10000)
                .get()

            // Try to find description in different selectors, preserving line breaks
            val description = extractTextWithLineBreaks(doc.select(".description__text")).takeIf { it.isNotBlank() }
                ?: extractTextWithLineBreaks(doc.select(".show-more-less-html__markup")).takeIf { it.isNotBlank() }
                ?: extractTextWithLineBreaks(doc.select("div[class*=description]")).takeIf { it.isNotBlank() }
                ?: doc.select("meta[name=description]").attr("content").takeIf { it.isNotBlank() }
                ?: "Unable to scrape job description from the provided URL."

            description
        } catch (e: Exception) {
            "Error scraping job: ${e.message}"
        }
    }

    private fun extractTextWithLineBreaks(element: org.jsoup.select.Elements): String {
        if (element.isEmpty()) return ""

        // Convert HTML to text while preserving line breaks
        val htmlContent = element.first()?.html() ?: return ""

        return htmlContent
            // Replace <br> tags with newlines
            .replace(Regex("<br\\s*/?>"), "\n")
            // Replace closing paragraph and div tags with double newlines
            .replace(Regex("</p>"), "\n\n")
            .replace(Regex("</div>"), "\n")
            .replace(Regex("</li>"), "\n")
            // Replace list items with bullet points
            .replace(Regex("<li[^>]*>"), "• ")
            // Remove all other HTML tags
            .replace(Regex("<[^>]+>"), "")
            // Decode HTML entities
            .let { org.jsoup.parser.Parser.unescapeEntities(it, false) }
            // Clean up multiple newlines (more than 2 consecutive)
            .replace(Regex("\n{3,}"), "\n\n")
            // Trim each line
            .lines()
            .joinToString("\n") { it.trim() }
            // Remove leading/trailing whitespace
            .trim()
    }
}

