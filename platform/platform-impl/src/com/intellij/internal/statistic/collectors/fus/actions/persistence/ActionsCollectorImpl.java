// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.ide.actions.ActionsCollector;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.internal.statistic.utils.StatisticsUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.FusAwareAction;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.lang.ref.WeakReference;
import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public class ActionsCollectorImpl extends ActionsCollector {
  public static final String DEFAULT_ID = "third.party";

  private static final ActionsBuiltInAllowedlist ourAllowedlist = ActionsBuiltInAllowedlist.getInstance();
  private static final Map<AnActionEvent, Stats> ourStats = ContainerUtil.createWeakMap();

  @Override
  public void record(@Nullable String actionId, @Nullable InputEvent event, @NotNull Class context) {
    recordCustomActionInvoked(null, actionId, event, context);
  }

  /** @noinspection unused*/
  public static void recordCustomActionInvoked(@Nullable Project project, @Nullable String actionId, @Nullable InputEvent event, @NotNull Class<?> context) {
    String recorded = StringUtil.isNotEmpty(actionId) && ourAllowedlist.isCustomAllowedAction(actionId) ? actionId : DEFAULT_ID;
    ActionsEventLogGroup.CUSTOM_ACTION_INVOKED.log(project, recorded, new FusInputEvent(event, null));
  }

  @Override
  public void record(@Nullable Project project, @Nullable AnAction action, @Nullable AnActionEvent event, @Nullable Language lang) {
    recordActionInvoked(project, action, event, Collections.singletonList(EventFields.CurrentFile.with(lang)));
  }

  public static void recordActionInvoked(@Nullable Project project,
                                         @Nullable AnAction action,
                                         @Nullable AnActionEvent event,
                                         @NotNull List<EventPair<?>> customData) {
    record(ActionsEventLogGroup.ACTION_INVOKED, project, action, event, customData);
  }

  public static void record(VarargEventId eventId,
                            @Nullable Project project,
                            @Nullable AnAction action,
                            @Nullable AnActionEvent event,
                            @Nullable List<EventPair<?>> customData) {
    if (action == null) return;
    PluginInfo info = PluginInfoDetectorKt.getPluginInfo(action.getClass());

    List<EventPair<?>> data = new ArrayList<>();
    data.add(EventFields.PluginInfoFromInstance.with(action));

    if (event != null) {
      if (action instanceof ToggleAction) {
        data.add(ActionsEventLogGroup.TOGGLE_ACTION.with(((ToggleAction)action).isSelected(event)));
      }
      data.addAll(actionEventData(event));
    }
    if (project != null && !project.isDisposed()) {
      data.add(ActionsEventLogGroup.DUMB.with(DumbService.isDumb(project)));
    }
    if (customData != null) {
      data.addAll(customData);
    }
    addActionClass(data, action, info);
    eventId.log(project, data);
  }

  public static @NotNull List<@NotNull EventPair<?>> actionEventData(@NotNull AnActionEvent event) {
    List<EventPair<?>> data = new ArrayList<>();
    data.add(EventFields.InputEvent.with(FusInputEvent.from(event)));
    data.add(EventFields.ActionPlace.with(event.getPlace()));
    data.add(ActionsEventLogGroup.CONTEXT_MENU.with(event.isFromContextMenu()));
    return data;
  }

  @NotNull
  public static String addActionClass(@NotNull List<EventPair<?>> data,
                                      @NotNull AnAction action,
                                      @NotNull PluginInfo info) {
    String actionClassName = info.isSafeToReport() ? action.getClass().getName() : DEFAULT_ID;
    String actionId = getActionId(info, action);
    if (action instanceof ActionWithDelegate) {
      Object delegate = ((ActionWithDelegate<?>)action).getDelegate();
      PluginInfo delegateInfo = PluginInfoDetectorKt.getPluginInfo(delegate.getClass());
      if (delegate instanceof AnAction) {
        AnAction delegateAction = (AnAction)delegate;
        actionId = getActionId(delegateInfo, delegateAction);
      } else {
        actionId = delegateInfo.isSafeToReport() ? delegate.getClass().getName() : DEFAULT_ID;
      }
      data.add(ActionsEventLogGroup.ACTION_CLASS.with(actionId));
      data.add(ActionsEventLogGroup.ACTION_PARENT.with(actionClassName));
    }
    else {
      data.add(ActionsEventLogGroup.ACTION_CLASS.with(actionClassName));
    }
    data.add(ActionsEventLogGroup.ACTION_ID.with(actionId));
    return actionId;
  }

  public static void addActionClass(@NotNull FeatureUsageData data,
                                      @NotNull AnAction action,
                                      @NotNull PluginInfo info) {
    List<EventPair<?>> list = new ArrayList<>();
    addActionClass(list, action, info);
    for (EventPair<?> pair : list) {
      data.addData(pair.component1().getName(), pair.component2().toString());
    }
  }


  @NotNull
  private static String getActionId(@NotNull PluginInfo pluginInfo, @NotNull AnAction action) {
    if (!pluginInfo.isSafeToReport()) {
      return DEFAULT_ID;
    }
    String actionId = ActionManager.getInstance().getId(action);
    if (actionId == null && action instanceof ActionIdProvider) {
      actionId = ((ActionIdProvider)action).getId();
    }
    if (actionId != null && !canReportActionId(actionId)) {
      return action.getClass().getName();
    }
    if (actionId == null) {
      actionId = ourAllowedlist.getDynamicActionId(action);
    }
    return actionId != null ? actionId : action.getClass().getName();
  }

  public static boolean canReportActionId(@NotNull String actionId) {
    return ourAllowedlist.isAllowedActionId(actionId);
  }

  @Override
  public void onActionConfiguredByActionId(@NotNull AnAction action, @NotNull String actionId) {
    ourAllowedlist.registerDynamicActionId(action, actionId);
  }

  /** @noinspection unused*/
  public static void onActionLoadedFromXml(@NotNull AnAction action, @NotNull String actionId, @Nullable IdeaPluginDescriptor plugin) {
    ourAllowedlist.addActionLoadedFromXml(actionId, plugin);
  }

  public static void onActionsLoadedFromKeymapXml(@NotNull Keymap keymap, @NotNull Set<String> actionIds) {
    ourAllowedlist.addActionsLoadedFromKeymapXml(keymap, actionIds);
  }

  /** @noinspection unused*/
  public static void onBeforeActionInvoked(@NotNull AnAction action, @NotNull AnActionEvent event) {
    Stats stats = new Stats(event.getProject());
    ourStats.put(event, stats);
  }

  /** @noinspection unused*/
  public static void onAfterActionInvoked(@NotNull AnAction action, @NotNull AnActionEvent event, @NotNull AnActionResult result) {
    Stats stats = ourStats.remove(event);
    long durationMillis = stats != null ? TimeoutUtil.getDurationMillis(stats.start) : -1;

    List<EventPair<?>> data = new ArrayList<>();
    if (stats != null && stats.isDumb != null) {
      data.add(ActionsEventLogGroup.DUMB_START.with(stats.isDumb));
    }

    ObjectEventData reportedResult = toReportedResult(result);
    data.add(ActionsEventLogGroup.RESULT.with(reportedResult));

    Project project = stats != null ? stats.projectRef.get() : null;
    addLanguageContextFields(project, event, data);
    if (action instanceof FusAwareAction) {
      List<EventPair<?>> additionalUsageData = ((FusAwareAction)action).getAdditionalUsageData(event);
      data.add(ActionsEventLogGroup.ADDITIONAL.with(new ObjectEventData(additionalUsageData)));
    }

    data.add(EventFields.DurationMs.with(StatisticsUtil.INSTANCE.roundDuration(durationMillis)));
    recordActionInvoked(project, action, event, data);
  }

  @NotNull
  private static ObjectEventData toReportedResult(@NotNull AnActionResult result) {
    if (result.isPerformed()) {
      return new ObjectEventData(ActionsEventLogGroup.RESULT_TYPE.with("performed"));
    }

    if (result == AnActionResult.IGNORED) {
      return new ObjectEventData(ActionsEventLogGroup.RESULT_TYPE.with("ignored"));
    }

    Throwable error = result.getFailureCause();
    if (error != null) {
      return new ObjectEventData(
        ActionsEventLogGroup.RESULT_TYPE.with("failed"),
        ActionsEventLogGroup.ERROR.with(error.getClass())
      );
    }
    return new ObjectEventData(ActionsEventLogGroup.RESULT_TYPE.with("unknown"));
  }

  private static void addLanguageContextFields(@Nullable Project project, @NotNull AnActionEvent event, @NotNull List<EventPair<?>> data) {
    // we try to avoid as many problems as possible, because
    // 1. non-async dataContext can fail due to advanced event-count, or freeze EDT on slow GetDataRules
    // 2. async dataContext can fail due to slow GetDataRules prohibition on EDT
    DataContext dataContext = dataId -> Utils.getRawDataIfCached(event.getDataContext(), dataId);

    Language hostFileLanguage = getHostFileLanguage(dataContext, project);
    data.add(EventFields.CurrentFile.with(hostFileLanguage));
    if (hostFileLanguage == null || hostFileLanguage == PlainTextLanguage.INSTANCE) {
      PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
      Language language = file != null ? file.getLanguage() : null;
      data.add(EventFields.Language.with(language));
    }
  }

  private static @Nullable Language getHostFileLanguage(@NotNull DataContext dataContext, @Nullable Project project) {
    if (project == null) return null;
    Editor editor = CommonDataKeys.HOST_EDITOR.getData(dataContext);
    if (editor == null) return null;
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    return file != null ? file.getLanguage() : null;
  }

  private static final class Stats {
    final long start = System.nanoTime();
    WeakReference<Project> projectRef;
    final Boolean isDumb;

    private Stats(Project project) {
      this.projectRef = new WeakReference<>(project);
      this.isDumb = project != null && !project.isDisposed() ? DumbService.isDumb(project) : null;
    }
  }
}
