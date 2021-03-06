package com.codicesoftware.plugins.hudson.actions;

import com.codicesoftware.plugins.hudson.PlasticSCM;
import com.codicesoftware.plugins.hudson.model.ChangeSet;
import com.codicesoftware.plugins.hudson.model.Server;
import com.codicesoftware.plugins.hudson.model.Workspace;
import com.codicesoftware.plugins.hudson.model.Workspaces;

import hudson.FilePath;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CheckoutAction {
    private final String workspaceName;
    private final String selector;
    private final boolean useUpdate;

    private static Pattern windowsPathPattern = Pattern.compile("^[a-zA-Z]:\\\\.*$");

    public CheckoutAction(String workspaceName, String selector, boolean useUpdate) {
        this.workspaceName = workspaceName;
        this.selector = removeNewLinesFromSelector(selector);
        this.useUpdate = useUpdate;
    }

    public List<ChangeSet> checkout(Server server, FilePath workspacePath,
            Calendar lastBuildTimestamp, Calendar currentBuildTimestamp)
            throws IOException, InterruptedException, ParseException {
        Workspaces workspaces = server.getWorkspaces();

        if (mustDeleteWorkspace(workspaces, workspaceName, workspacePath, useUpdate)) {
            Workspace workspace = workspaces.getWorkspace(workspaceName);
            workspaces.deleteWorkspace(workspace);
            new FilePath(new File(workspace.getPath())).deleteContents();
        }

        Workspace workspace;
        if (!workspaces.exists(workspaceName)) {
            if (!useUpdate && workspacePath.exists()) {
                workspacePath.deleteContents();
            }
            workspace = workspaces.newWorkspace(workspacePath, workspaceName, selector);
            server.getFiles(workspace.getPath());
        } else {
            workspace = workspaces.getWorkspace(workspaceName);
            if (mustUpdateSelector(workspace)) {
                workspace.setSelector(selector);
                workspaces.setWorkspaceSelector(workspacePath, workspace);
            } else {
            	server.getFiles(workspace.getPath());
            }
        }

        if (lastBuildTimestamp != null) {
            return server.getDetailedHistory(
                workspace.getPath(), lastBuildTimestamp, currentBuildTimestamp);
        }
        return new ArrayList<ChangeSet>();
    }

    private boolean mustUpdateSelector(Workspace workspace) {
        String wkSelector = removeNewLinesFromSelector(workspace.getSelector());
        String currentSelector = removeNewLinesFromSelector(selector);

        return !wkSelector.equals(currentSelector);
    }

    private String removeNewLinesFromSelector(String selector) {
        return selector.trim().replace("\r\n", "").replace("\n", "").replace("\r", "");
    }

    private static boolean mustDeleteWorkspace(
            Workspaces workspaces,
            String currentWorkspaceName,
            FilePath expectedWorkspacePath,
            boolean shouldUseUpdate) throws IOException, InterruptedException {
        if (!workspaces.exists(currentWorkspaceName))
            return false;
        if (!shouldUseUpdate)
            return true;

        String currentWorkspacePath = workspaces.getWorkspace(currentWorkspaceName).getPath();
        return !isSamePath(expectedWorkspacePath.getRemote(), currentWorkspacePath);
    }

    private static boolean isSamePath(String expected, String actual) {
        Matcher windowsPathMatcher = windowsPathPattern.matcher(actual);
        if (windowsPathMatcher.matches()) {
            return actual.equalsIgnoreCase(expected);
        }
        return actual.equals(expected);
    }
}