/*
 * SonarQube :: GitLab Plugin
 * Copyright (C) 2016-2025 Talanlabs
 * gabriel.allaigre@gmail.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package com.talanlabs.sonar.plugins.gitlab;

import com.talanlabs.sonar.plugins.gitlab.models.ReportIssue;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.utils.System2;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InlineTemplateTest {

    private static final String TEMPLATE = "<#list issues() as issue>\n" + "<@p issue=issue/>\n" + "</#list>\n" + "<#macro p issue>\n"
            + "${emojiSeverity(issue.severity)} ${issue.message} [:blue_book:](${ruleLink(issue.ruleKey)})\n" + "</#macro>";

    private MapSettings settings;
    private GitLabPluginConfiguration config;

    @Before
    public void setUp() {
        settings = new MapSettings(new PropertyDefinitions(System2.INSTANCE, PropertyDefinition.builder(CoreProperties.SERVER_BASE_URL).name("Server base URL")
                .description("HTTP URL of this SonarQube server, such as <i>http://yourhost.yourdomain/sonar</i>. This value is used i.e. to create links in emails.")
                .category(CoreProperties.CATEGORY_GENERAL).defaultValue("http://localhost:9000").build()).addComponents(GitLabPlugin.definitions()));

        settings.setProperty(CoreProperties.SERVER_BASE_URL, "http://myserver");
        settings.setProperty(GitLabPlugin.GITLAB_COMMIT_SHA, "abc123");

        config = new GitLabPluginConfiguration(settings.asConfig(), new System2());

        settings.setProperty(GitLabPlugin.GITLAB_INLINE_TEMPLATE, TEMPLATE);
    }

    @Test
    public void testOneIssue() {
        ReportIssue r1 =ReportIssue.newBuilder().issue(Utils.newIssue("component", null, 1, Severity.INFO, true, "Issue", "rule")).revision(null).url("lalal").file("file").ruleLink(
                "http://myserver/coding_rules#rule_key=repo%3Arule").reportedOnDiff(true).build();

        Assertions.assertThat(new InlineCommentBuilder(config, "123", null, 1, Collections.singletonList(r1), new MarkDownUtils()).buildForMarkdown())
                .isEqualTo(":information_source: Issue [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)\n");
    }

    @Test
    public void testTwoIssue() {
        List<ReportIssue> ris = Stream.iterate(0, i -> i++).limit(2)
                .map(i ->ReportIssue.newBuilder().issue(Utils.newIssue("component", null, 1, Severity.INFO, true, "Issue", "rule")).revision(null).url("lalal").file("file").ruleLink(
                        "http://myserver/coding_rules#rule_key=repo%3Arule").reportedOnDiff(true).build()).collect(Collectors.toList());

        Assertions.assertThat(new InlineCommentBuilder(config, "123", null, 1, ris, new MarkDownUtils()).buildForMarkdown()).isEqualTo(
                ":information_source: Issue [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)\n"
                        + ":information_source: Issue [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)\n");
    }

    @Test
    public void testUnescapeHTML() {
        settings.setProperty(GitLabPlugin.GITLAB_INLINE_TEMPLATE, TEMPLATE + "&agrave;&acirc;&eacute;&ccedil;");

        ReportIssue r1 =ReportIssue.newBuilder().issue(Utils.newIssue("component", null, 1, Severity.INFO, true, "Issue", "rule")).revision(null).url("lalal").file("file").ruleLink(
                "http://myserver/coding_rules#rule_key=repo%3Arule").reportedOnDiff(true).build();

        Assertions.assertThat(new InlineCommentBuilder(config, "123", null, 1, Collections.singletonList(r1), new MarkDownUtils()).buildForMarkdown())
                .isEqualTo(":information_source: Issue [:blue_book:](http://myserver/coding_rules#rule_key=repo%3Arule)\nàâéç");
    }

    @Test
    public void testTemplateNewVariables() {
        // Set up configuration values for the new variables
        settings.setProperty(GitLabPlugin.GITLAB_CI_MERGE_REQUEST_IID, "42");
        settings.setProperty(GitLabPlugin.SONAR_PULL_REQUEST_KEY, "123");
        settings.setProperty(GitLabPlugin.GITLAB_STATUS_NAME, "custom-status");
        settings.setProperty(GitLabPlugin.GITLAB_PING_USER, "true");
        settings.setProperty(GitLabPlugin.GITLAB_ALL_ISSUES, "true");
        settings.setProperty(GitLabPlugin.GITLAB_ISSUE_FILTER, "MAJOR");
        settings.setProperty(GitLabPlugin.GITLAB_API_VERSION, "v4");
        settings.setProperty(GitLabPlugin.GITLAB_PREFIX_DIRECTORY, "/src/main");
        settings.setProperty(GitLabPlugin.GITLAB_UNIQUE_ISSUE_PER_INLINE, "true");
        settings.setProperty(GitLabPlugin.GITLAB_FAIL_ON_QUALITY_GATE, "true");
        settings.setProperty(GitLabPlugin.GITLAB_MERGE_REQUEST_DISCUSSION, "true");
        settings.setProperty("sonar.projectKey", "my-project");

        config = new GitLabPluginConfiguration(settings.asConfig(), new System2());

        String testTemplate = "MR IID: ${mergeRequestIid}, PR Key: ${pullRequestKey}, Status: ${statusName}, Project: ${projectKey}";

        settings.setProperty(GitLabPlugin.GITLAB_INLINE_TEMPLATE, testTemplate);

        ReportIssue r1 = ReportIssue.newBuilder().issue(Utils.newIssue("component", null, 1, Severity.INFO, true, "Issue", "rule")).revision(null).url("lalal").file("file").ruleLink(
                "http://myserver/coding_rules#rule_key=repo%3Arule").reportedOnDiff(true).build();

        Assertions.assertThat(new InlineCommentBuilder(config, "123", null, 1, Collections.singletonList(r1), new MarkDownUtils()).buildForMarkdown())
                .isEqualTo("MR IID: 42, PR Key: 123, Status: custom-status, Project: my-project");
    }

    @Test
    public void testTemplateNewVariablesDefaults() {
        // Test with default values
        config = new GitLabPluginConfiguration(settings.asConfig(), new System2());

        String testTemplate = "Ping: ${pingUser?c}, All: ${allIssues?c}, Unique: ${uniqueIssuePerInline?c}, Discussion: ${isMergeRequestDiscussion?c}";

        settings.setProperty(GitLabPlugin.GITLAB_INLINE_TEMPLATE, testTemplate);

        ReportIssue r1 = ReportIssue.newBuilder().issue(Utils.newIssue("component", null, 1, Severity.INFO, true, "Issue", "rule")).revision(null).url("lalal").file("file").ruleLink(
                "http://myserver/coding_rules#rule_key=repo%3Arule").reportedOnDiff(true).build();

        Assertions.assertThat(new InlineCommentBuilder(config, "123", null, 1, Collections.singletonList(r1), new MarkDownUtils()).buildForMarkdown())
                .isEqualTo("Ping: false, All: false, Unique: false, Discussion: false");
    }
}
