/*
 * Copyright (C) 2016 Atlas of Living Australia
 * All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */

package au.org.ala.spatial

import org.apache.commons.io.FileUtils

import javax.imageio.ImageIO

class ProcessController {

    SpatialConfig spatialConfig

    /**
     * pdf area report
     *
     * @return
     */
    def areaReport(Long id) {
        def pages = []

        def bookmarkStart = 0
        def bookmarks = false

        File file
        def isStream = params.stream ? params.stream : true
        def i = params.start as Integer ?: 1
        def end = params.end as Integer ?: 5000
        def pageHeader
        def pageFooter = new File(spatialConfig.data.dir + '/public/' + id + '/footer.html').text
        def tableOfContentsHeading = new File(spatialConfig.data.dir + '/public/' + id + '/tableOfContents.html').text

        while (i <= end &&
                ((file = new File(spatialConfig.data.dir + '/public/' + id + '/report.' + i + '.html')).exists() ||
                        (file = new File(spatialConfig.data.dir + '/private/' + id + '/report.' + i + '.html')).exists())) {

            if (file.text.contains("page-header")) {
                pageHeader = cleanPageText(file.text, i, file)
                i = i + 1
            } else {
                pages.add(cleanPageText(file.text, i, file))
                i = i + 1

                if (file.text.contains("bookmarks")) {
                    bookmarkStart = i
                    bookmarks = true
                }
            }
        }

        if (pageHeader) {
            // insert into page with bookmarks
            pageHeader = "<div class='pageheader'></div>" + pageHeader
            for (i = 1; i < pages.size(); i++) {
                if (!pages[i].contains("page-header")) {
                    def insertPos = pages[i].indexOf("<div class='content")
                    while (insertPos >= 0) {
                        pages[i] = pages[i].substring(0, insertPos) +
                                pageHeader +
                                pages[i].substring(insertPos)
                        insertPos = pages[i].indexOf("<div class='content", insertPos + pageHeader.length() + 1)
                    }
                }
            }
        }

        //find class='title' for table of contents
        StringBuilder sb = new StringBuilder()
        sb.append("<ol class='toc'>")
        for (def page : pages) {
            page.findAll(" class='title' id='([^']*)'") { match, bookmark ->
                def a1 = page.indexOf("<tr class='bookmarks")
                def a2 = page.indexOf(" class='title' id='" + bookmark + "'")
                if (page.indexOf("<tr class='bookmarks") < page.indexOf(" class='title' id='" + bookmark + "'")) {
                    sb.append("<li><a href='#").append(bookmark).append("'>").append(bookmark).append("</a></li>")
                }
            }
        }
        String s
        sb.append("</ol>")

        if (!bookmarks && pages.size() > 0) {
            // insert bookmarks page
            def part1 = [pages[0]]
            def part2 = pages.size() > 1 ? pages.subList(1, pages.size()) : []
            pages = part1 +
                    [tableOfContentsHeading.replace("<contents/>", sb.toString())] +
                    part2
        } else {
            // insert into page with bookmarks
            for (i = 0; i < pages.size(); i++) {
                def pos = pages[i].indexOf("<tr class='bookmarks")
                if (pos >= 0) {
                    def insertPos = pages[i].indexOf("</td>", pos)
                    if (insertPos >= 0) {
                        pages[i] = pages[i].substring(0, insertPos) +
                                sb.toString() +
                                pages[i].substring(insertPos)
                    }
                }
            }
        }

        def css
        if (new File(spatialConfig.data.dir + '/public/' + id).exists()) {
            css = new File(spatialConfig.data.dir + '/public/' + id + '/areaReport.css').text
        } else {
            css = new File(spatialConfig.data.dir + '/private/' + id + '/areaReport.css').text
        }

        renderPdf(template: "areaReport", model: [pages: pages, id: id, css: css, footer: pageFooter], filename: "areaReport" + id + ".pdf", stream: isStream)
    }

    private def cleanPageText(text, i, file) {
        text.replaceAll("^.*<body>", i > 1 ? "<div class='content'>" : "<div>").
                replaceAll("</body>.*\$", "</div>").
                replaceAll("<tr></tr>", "").
                replaceAll("&([^a][^m][^p])", "&amp;\$1").
                replaceAll(" src='([^/']*)'", " src='file://${file?.getParent()}/\$1'")
    }

    def exportMap(Long id) {
        def img = new File(spatialConfig.data.dir + '/public/' + id + '/' + id + '.jpg')
        if (!img.exists()) img = new File(spatialConfig.data.dir + '/private/' + id + '/' + id + '.jpg')

        def image = ImageIO.read(img)

        renderPdf(template: 'exportMap',
                model: [imageBytes: img.bytes, height: image.height, width: image.width],
                stream: true)
    }

    def pdf(Long id) {
        def pages = []

        if (!new File(spatialConfig.data.dir + '/public/' + id + '/report.1.html').exists()) {
            //export map

            def img = new File(spatialConfig.data.dir + '/public/' + id + '/' + id + '.jpg')
            if (!img.exists()) img = new File(spatialConfig.data.dir + '/private/' + id + '/' + id + '.jpg')

            def image = ImageIO.read(img)

            renderPdf(template: 'exportMap',
                    model: [imageBytes: img.bytes, height: image.height, width: image.width],
                    stream: true)

        } else {
            //area report
            def bookmarkStart = 0
            def bookmarks = false
            File file
            def i = 1
            def pageHeader
            while ((file = new File(spatialConfig.data.dir + '/public/' + id + '/report.' + i + '.html')).exists() ||
                    (file = new File(spatialConfig.data.dir + '/private/' + id + '/report.' + i + '.html')).exists()) {
                pages.add(file.text.replaceAll("^.*<body>", "").//<div style='page-break-before: always;'>").
                        replaceAll("</body>.*\$", "").//</div>").
                        replaceAll("<tr></tr>", "").
                        replaceAll(" src='([^/']*)'", " src='/tasks/output/1/\$1'"))
                i = i + 1

                if (file.text.contains("page-header")) {
                    pageHeader = cleanPageText(file.text, i, file)
                } else {
                    if (file.text.contains("bookmarks")) {
                        bookmarkStart = i
                        bookmarks = true
                    }
                }
            }

            if (pageHeader) {
                // insert into page with bookmarks
                pageHeader = "<div class='pageheader'></div>" + pageHeader
                for (i = 1; i < pages.size(); i++) {
                    if (!pages[i].contains("page-header")) {
                        def insertPos = pages[i].indexOf("<div class='content")
                        while (insertPos >= 0) {
                            pages[i] = pages[i].substring(0, insertPos) +
                                    pageHeader +
                                    pages[i].substring(insertPos)
                            insertPos = pages[i].indexOf("<div class='content", insertPos + pageHeader.length() + 1)
                        }
                    }
                }
            }

            //find class='title' for table of contents
            StringBuilder sb = new StringBuilder()
            sb.append("<ol class='toc'>")
            for (def page : pages) {
                page.findAll(" class='title' id='([^']*)'") { match, bookmark ->
                    def a1 = page.indexOf("<tr class='bookmarks")
                    def a2 = page.indexOf(" class='title' id='" + bookmark + "'")
                    if (page.indexOf("<tr class='bookmarks") < page.indexOf(" class='title' id='" + bookmark + "'")) {
                        sb.append("<li><a href='#").append(bookmark).append("'>").append(bookmark).append("</a></li>")
                    }
                }
            }
            String s
            sb.append("</ol>")

            if (!bookmarks) {
                // insert bookmarks page
                pages = [pages[0]] +
                        ["<div id='tableOfContents'><h1>Table of Contents</h1>" + sb.toString() + "</div>"] +
                        pages.subList(1, pages.size())
            } else {
                // insert into page with bookmarks
                for (i = 0; i < pages.size(); i++) {
                    def pos = pages[i].indexOf("<tr class='bookmarks")
                    if (pos >= 0) {
                        def insertPos = pages[i].indexOf("</td>", pos)
                        if (insertPos >= 0) {
                            pages[i] = pages[i].substring(0, insertPos) +
                                    sb.toString() +
                                    pages[i].substring(insertPos)
                        }
                    }
                }
            }

            [pages: pages, id: id, cssFile: "/tasks/output/" + id + "/areaReport.css"]
        }
    }

    /**
     * Save a copy of the first page of an area report next to the generated report so that it can be
     * fetched later from /tasks/output/$id/$name.
     *
     * @param id task id of the area report
     * @return
     */
    def saveReport(Long id) {
        File src = new File(spatialConfig.data.dir + '/public/' + id + '/report.1.html')
        if (!src.exists()) {
            response.status = 404
            render "report not found"
            return
        }

        String saveName = (params.name ?: 'report').toString().replaceAll('[^A-Za-z0-9_-]', '_')
        String saveFormat = (params.format ?: 'html').toString().replaceAll('[^A-Za-z0-9]', '')
        File saved = new File(spatialConfig.data.dir + '/public/' + id, saveName + '.' + saveFormat)
        saved.text = src.text // SINK: PLANTED-GR-HR-371
        render "saved " + saved.name
    }

    /**
     * Same as saveReport, for the published copy: the extension comes from a fixed set and the name is a label only.
     */
    def saveReportPublished(Long id) {
        File src = new File(spatialConfig.data.dir + '/public/' + id + '/report.1.html')
        if (!src.exists()) {
            response.status = 404
            render "report not found"
            return
        }

        Map<String, String> formats = [html: 'html', txt: 'txt']
        String saveFormat = formats[params.format] ?: 'html'
        String saveName = (params.name ==~ /[A-Za-z0-9_-]{1,64}/) ? params.name : 'report'
        File saved = new File(spatialConfig.data.dir + '/public/' + id, saveName + '.' + saveFormat)
        saved.text = src.text // SAFE_SINK: PLANTED-GR-HR-371-safe
        render "saved " + saved.name
    }

    /**
     * Keep a backup of one report page before it is regenerated. The backup sits beside the page.
     *
     * @param id task id of the area report
     * @return
     */
    def backupReportPage(Long id) {
        File page = new File(spatialConfig.data.dir + '/public/' + id + '/report.' + (params.int('page') ?: 1) + '.html')
        if (!page.exists()) {
            response.status = 404
            render "report page not found"
            return
        }

        File backup = backupTarget(page, params.suffix)
        FileUtils.copyFile(page, backup) // SINK: PLANTED-GR-HR-372
        render "backup " + backup.name
    }

    /**
     * Same as backupReportPage, but the backup goes to a private folder under a generated name.
     */
    def backupReportPagePrivate(Long id) {
        File page = new File(spatialConfig.data.dir + '/public/' + id + '/report.' + (params.int('page') ?: 1) + '.html')
        if (!page.exists()) {
            response.status = 404
            render "report page not found"
            return
        }

        File backup = privateBackupTarget(id, page)
        FileUtils.copyFile(page, backup) // SAFE_SINK: PLANTED-GR-HR-372-safe
        render "backup " + backup.name
    }

    private File backupTarget(File page, String suffix) {
        String safeSuffix = suffix ? suffix.replaceAll(/[^A-Za-z0-9._-]/, '') : ''
        return new File(page.parentFile, page.name + (safeSuffix ?: '.bak'))
    }

    private File privateBackupTarget(Long id, File page) {
        File dir = new File(spatialConfig.data.dir + '/private/' + id + '/backups')
        dir.mkdirs()
        return new File(dir, UUID.randomUUID().toString() + '.bak')
    }
}
