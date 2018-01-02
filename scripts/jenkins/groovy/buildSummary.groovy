import hudson.tasks.test.AbstractTestResultAction

def call() {
    return new BuildSummary()
}

class BuildSummary {

    public static final String RESULT_SUCCESS = 'success'
    public static final String RESULT_FAILURE = 'failure'

    private final List<Stage> stageSummaries = []
    private final List<Section> sections = []

    Section addSection(final context, final String title, final String contentTemplate) {
        if (findSectionWithTitle(title) != null) {
            throw new IllegalArgumentException('Section with title %s already.exists'.format(title))
        }
        def section = new Section(title, contentTemplate)
        sections.add(section)
        updateJobDescription(context)
        return section
    }

    Section updateSection(final context, final String title, final String newContent) {
        final Section section = findSectionWithTitleOrThrow(title)
        section.setContent(newContent)
        updateJobDescription(context)
        return section
    }

    Section addChangesSectionIfNecessary(final context) {
        def REPO_URL = 'https://github.com/h2oai/h2o-3'

        def changesContent = ''
        context.currentBuild.getRawBuild().getChangeSets().each { changeSetList ->
            if (changeSetList.getBrowser().getRepoUrl() == REPO_URL) {
                changesContent += "<ul>"
                changeSetList.each { changeSet ->
                    changesContent += """
                      <li>
                        <a href=\"${REPO_URL}/commit/${changeSet.getRevision()}\">
                          <strong>${changeSet.getRevision().substring(0, 8)}</strong>
                        </a> by <strong>${changeSet.getAuthorEmail()}</strong> - ${changeSet.getMsg()}
                      </li>
                    """
                }
                changesContent += "</ul>"
            }
        }

        Section section = null
        if (changesContent != '') {
            section = addSection(this, 'Changes', changesContent)
        }
        return section
    }

    @NonCPS
    Section addTestsSection(final context) {
        def build = context.currentBuild.rawBuild
        final Section section = addSection(context, 'Failed Tests', getTestsSectionContent(build))
        updateJobDescription(context)
        return section
    }

    @NonCPS
    Section updateTestsSection(final context) {
        def build = context.currentBuild.rawBuild
        return updateSection(context, 'Failed Tests', getTestsSectionContent(build))
    }

    Stage addStageSummary(final context, final String stageName) {
        if (findStageSummaryWithName(stageName) != null) {
            throw new IllegalArgumentException("Stage Summary with name %s already defined".format(stageName))
        }
        def stage = new Stage(stageName)
        stageSummaries.add(stage)
        updateJobDescription(context)
        return stage
    }

    Stage markStageSuccessful(final context, final String stageName) {
        final Stage stage = setStageResult(stageName, RESULT_SUCCESS)
        updateJobDescription(context)
        return stage
    }

    Stage markStageFailed(final context, final String stageName) {
        final Stage stage = setStageResult(stageName, RESULT_FAILURE)
        updateJobDescription(context)
        return stage
    }

    Stage setStageDetails(final context, final String stageName, final String nodeName, final String workspacePath) {
        def stage = findStageSummaryWithNameOrThrow(stageName)
        stage.setNodeName(nodeName)
        stage.setWorkspace(workspacePath)
        updateJobDescription(context)
        return stage
    }

    @Override
    String toString() {
        return "${stageSummaries}"
    }

    private getTestsSectionContent(final build) {
        def testResultsAction = build.getAction(AbstractTestResultAction.class)
        def testsContent = "<p>No tests were run.</p>"
        if (testResultsAction != null) {
            def failedTests = testResultsAction.getFailedTests()
            if (failedTests.isEmpty()) {
                testsContent = "<p>All tests passed!</p>"
            } else {
                testsContent = "<ul>"
                for (failedTest in failedTests) {
                    testsContent += """
                        <li>${failedTest.getFullDisplayName()}</li>
                    """
                }
                testsContent += "</ul>"
            }
        }
        return testsContent
    }

    private void updateJobDescription(final context) {
        def stagesSection = ''
        def stagesTableBody = ''

        if (!stageSummaries.isEmpty()) {
            for (stageSummary in stageSummaries) {
                def nodeName = stageSummary.getNodeName() == null ? 'Not yet allocated' : stageSummary.getNodeName()
                def result = stageSummary.getResult() == null ? 'Pending' : stageSummary.getResult()
                stagesTableBody += """
          <tr style="background-color: ${stageResultToBgColor(stageSummary.getResult())}">
            <td style="border: 1px solid black; padding: 0.2em 1em">${stageSummary.getName()}</td>
            <td style="border: 1px solid black; padding: 0.2em 1em">${nodeName}</td>
            <td style="border: 1px solid black; padding: 0.2em 1em">${stageSummary.getWorkspace()}</td>
            <td style="border: 1px solid black; padding: 0.2em 1em">${result.capitalize()}</td>
          </tr>
        """
            }
            stagesSection = createHTMLForSection('Stages Overview', """
                <table style="margin-left: 1em; border-collapse: collapse">
                  <thead>
                    <tr>
                      <th style="border: 1px solid black; padding: 0.5em">Name</th>
                      <th style="border: 1px solid black; padding: 0.5em">Node</th>
                      <th style="border: 1px solid black; padding: 0.5em">Workspace</th>
                      <th style="border: 1px solid black; padding: 0.5em">Result</th>
                    </tr>
                  </thead>
                  <tbody>
                    ${stagesTableBody}
                  </tbody>
                </table>
            """, false)
        }

        String sectionsHTML = ''
        for (section in sections) {
            sectionsHTML += createHTMLForSection(section.getTitle(), section.getContent(), true)
        }

        context.currentBuild.description = """
      <div style="border: 1px solid #d3d7cf; padding: 0em 1em 1em 1em;">
        ${sectionsHTML}
        ${stagesSection}  
      </div>
    """
    }

    private String createHTMLForSection(final String title, final String content, final boolean bottomBorder=true) {
        def bottomBorderValue = ''
        if (bottomBorder) {
            bottomBorderValue = 'border-bottom: 1px dashed gray;'
        }
        return """
            <div style="margin-bottom: 15px;${bottomBorderValue}">
                <h3>${title}</h3>
                <div style="margin-left: 15px;">
                    ${content}
                </div>
            </div>
        """
    }

    private setStageResult(final String stageName, final String result) {
        def summary = findStageSummaryWithNameOrThrow(stageName)
        summary.setResult(result)
        return summary
    }

    private String stageResultToBgColor(final String result) {
        def BG_COLOR_SUCCESS = '#7fce67'
        def BG_COLOR_FAILURE = '#d56060'
        def BG_COLOR_OTHER = '#fbf78b'

        if (result == RESULT_SUCCESS) {
            return BG_COLOR_SUCCESS
        }
        if (result == RESULT_FAILURE) {
            return BG_COLOR_FAILURE
        }
        return BG_COLOR_OTHER
    }

    private def findStageSummaryWithName(final String stageName) {
        return stageSummaries.find({it.getName() == stageName})
    }

    private def findStageSummaryWithNameOrThrow(final String stageName) {
        def summary = findStageSummaryWithName(stageName)
        if (summary == null) {
            throw new IllegalStateException("Cannot find StageSummary with name %s".format(stageName))
        }
        return summary
    }

    private def findSectionWithTitle(final String title) {
        return sections.find({it.getTitle() == title})
    }

    private def findSectionWithTitleOrThrow(final String title) {
        def section = findSectionWithTitle(title)
        if (section == null) {
            throw new IllegalStateException("Cannot find section with title %s".format(title))
        }
        return section
    }

    static class Section {
        private final String title
        private String content

        Section(String title, String content) {
            this.title = title
            this.content = content
        }

        String getTitle() {
            return title
        }

        String getContent() {
            return content
        }

        void setContent(String content) {
            this.content = content
        }
    }

    static class Stage {
        private final String name
        private String nodeName
        private String workspace
        private String result

        Stage(String name) {
            this.name = name
        }

        String getName() {
            return name
        }

        String getNodeName() {
            return nodeName
        }

        void setNodeName(String nodeName) {
            this.nodeName = nodeName
        }

        String getWorkspace() {
            return workspace
        }

        void setWorkspace(String workspace) {
            this.workspace = workspace
        }

        String getResult() {
            return result
        }

        void setResult(String result) {
            this.result = result
        }
    }

}

return this