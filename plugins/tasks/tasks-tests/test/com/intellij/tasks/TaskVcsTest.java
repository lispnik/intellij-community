/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.tasks;

import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs;
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog;
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcses;
import com.intellij.tasks.impl.LocalTaskImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 3/5/12
 */
public class TaskVcsTest extends TaskManagerTestCase {

  public void testCreateChangelistForLocalTask() throws Exception {
    LocalTaskImpl task = new LocalTaskImpl("TEST-002", "Summary2");
    createChangelist(task);
    assertEquals("Summary2", myManager.getOpenChangelists(task).get(0).name);
  }

  public void  testActivateTask() throws Exception {
    Task task = myRepository.findTask("TEST-001");
    assertNotNull(task);
    List<ChangeListInfo> changelists = myManager.getOpenChangelists(task);
    assertEquals(0, changelists.size());
    myManager.activateTask(task, false, true);
    LocalTask localTask = myManager.getActiveTask();
    assertEquals(task, localTask);
    changelists = myManager.getOpenChangelists(localTask);
    assertEquals(1, changelists.size());
    assertEquals("TEST-001 Summary", changelists.get(0).name);
  }

  public void testCreateComment() throws Exception {
    myRepository.setShouldFormatCommitMessage(true);
    myRepository.setCommitMessageFormat("{id} {summary} {number} {project}");
    Task task = myRepository.findTask("TEST-001");
    assertNotNull(task);
    myManager.activateTask(task, false, true);
    LocalTask localTask = myManager.getActiveTask();
    assertNotNull(localTask);
    assertEquals("TEST-001 Summary 001 TEST", myManager.getOpenChangelists(localTask).get(0).comment);
  }

  public void testSaveContextOnCommit() throws Exception {
    Task task = myRepository.findTask("TEST-001");
    assertNotNull(task);
    myManager.activateTask(task, false, true);

    assertEquals(1, myManager.getLocalTasks().length);
    LocalTask localTask = myManager.getActiveTask();
    List<ChangeListInfo> changelists = myManager.getOpenChangelists(localTask);

    ChangeListInfo info = changelists.get(0);
    LocalChangeList changeList = ChangeListManager.getInstance(getProject()).getChangeList(info.id);
    assertNotNull(changeList);
    assertEquals(changeList.getId(), localTask.getAssociatedChangelistId());

    CommitChangeListDialog.commitChanges(getProject(), Collections.<Change>emptyList(), changeList, null, changeList.getName());

    assertEquals(1, myManager.getLocalTasks().length); // no extra task created

    LocalTask associatedTask = myManager.getAssociatedTask(changeList);
    assertNotNull(associatedTask); // association should survive
  }

  public void testProjectWithDash() throws Exception {
    LocalTaskImpl task = new LocalTaskImpl("foo-bar-001", "summary") {
      @Override
      public TaskRepository getRepository() {
        return myRepository;
      }
    };
    assertEquals("foo-bar", task.getProject());
    assertEquals("001", task.getNumber());
    String name = myManager.getChangelistName(task);
    assertEquals("foo-bar-001 summary", name);
  }

  public void testIds() throws Exception {
    LocalTaskImpl task = new LocalTaskImpl("", "");
    assertEquals("", task.getNumber());
    assertEquals(null, task.getProject());

    task = new LocalTaskImpl("-", "");
    assertEquals("-", task.getNumber());
    assertEquals(null, task.getProject());

    task = new LocalTaskImpl("foo", "");
    assertEquals("foo", task.getNumber());
    assertEquals(null, task.getProject());

    task = new LocalTaskImpl("112", "");
    assertEquals("112", task.getNumber());
    assertEquals(null, task.getProject());
  }

  private void createChangelist(LocalTask localTask) throws InterruptedException {
    clearChangeLists();
    if (localTask.isActive()) {
      assertEquals(1, myManager.getOpenChangelists(localTask).size());
    } else {
      assertEquals(0, myManager.getOpenChangelists(localTask).size());
    }
    myManager.createChangeList(localTask, myManager.getChangelistName(localTask));
    List<ChangeListInfo> list = myManager.getOpenChangelists(localTask);
    assertEquals(1, list.size());
    ChangeListInfo changeListInfo = list.get(0);
    assertEquals(changeListInfo.id, localTask.getAssociatedChangelistId());
  }

  private TestRepository myRepository;
  private MockAbstractVcs myVcs;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myVcs = new MockAbstractVcs(getProject());
    AllVcses.getInstance(getProject()).registerManually(myVcs);
    ChangeListManager.getInstance(getProject()).addChangeList("Default", "");

    ProjectLevelVcsManager.getInstance(getProject()).setDirectoryMapping("", myVcs.getName());
    ProjectLevelVcsManager.getInstance(getProject()).hasActiveVcss();
    myRepository = new TestRepository();
    myRepository.setTasks(new Task() {
      @NotNull
      @Override
      public String getId() {
        return "TEST-001";
      }

      @NotNull
      @Override
      public String getSummary() {
        return "Summary";
      }

      @Override
      public String getDescription() {
        return null;
      }

      @NotNull
      @Override
      public Comment[] getComments() {
        return new Comment[0];
      }

      @Override
      public Icon getIcon() {
        return null;
      }

      @NotNull
      @Override
      public TaskType getType() {
        return TaskType.BUG;
      }

      @Override
      public Date getUpdated() {
        return null;
      }

      @Override
      public Date getCreated() {
        return null;
      }

      @Override
      public boolean isClosed() {
        return false;
      }

      @Override
      public boolean isIssue() {
        return false;
      }

      @Override
      public String getIssueUrl() {
        return null;
      }

      @Override
      public TaskRepository getRepository() {
        return myRepository;
      }
    });
    myManager.setRepositories(Collections.singletonList(myRepository));
  }

  @Override
  protected void tearDown() throws Exception {
    AllVcses.getInstance(getProject()).unregisterManually(myVcs);
    super.tearDown();
  }

  private void clearChangeLists() {
    ChangeListManagerImpl changeListManager = (ChangeListManagerImpl)ChangeListManager.getInstance(getProject());
    List<LocalChangeList> lists = changeListManager.getChangeListsCopy();
    for (LocalChangeList list : lists) {
      if (!list.isDefault()) {
        changeListManager.removeChangeList(list);
      }
    }
  }
}
