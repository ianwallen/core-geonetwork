/*
 * Copyright (C) 2001-2016 Food and Agriculture Organization of the
 * United Nations (FAO-UN), United Nations World Food Programme (WFP)
 * and United Nations Environment Programme (UNEP)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301, USA
 *
 * Contact: Jeroen Ticheler - FAO - Viale delle Terme di Caracalla 2,
 * Rome - Italy. email: geonetwork@osgeo.org
 */

(function () {
  goog.provide("gn_search_default_directive");

  var module = angular.module("gn_search_default_directive", []);

  module.directive("gnInfoList", [
    "gnMdView",
    function (gnMdView) {
      return {
        restrict: "A",
        replace: true,
        templateUrl: "../../catalog/views/default/directives/" + "partials/infolist.html",
        link: function linkFn(scope, element, attr) {
          scope.showMore = function (isDisplay) {
            var div = $("#gn-info-list" + this.md.uuid);
            $(div.children()[isDisplay ? 0 : 1]).addClass("hidden");
            $(div.children()[isDisplay ? 1 : 0]).removeClass("hidden");
          };
          scope.go = function (uuid) {
            gnMdView(index, md, records);
            gnMdView.setLocationUuid(uuid);
          };
        }
      };
    }
  ]);

  module.directive("gnAttributeTableRenderer", [
    "gnMdView",
    function (gnMdView) {
      return {
        restrict: "A",
        replace: true,
        templateUrl:
          "../../catalog/views/default/directives/" + "partials/attributetable.html",
        scope: {
          attributeTable: "=gnAttributeTableRenderer"
        },
        link: function linkFn(scope, element, attrs) {
          if (
            angular.isDefined(scope.attributeTable) &&
            !angular.isArray(scope.attributeTable)
          ) {
            scope.attributeTable = [scope.attributeTable];
          }
          scope.columnVisibility = {
            code: false
          };
          angular.forEach(scope.attributeTable, function (elem) {
            if (elem.code > "") {
              scope.columnVisibility.code = true;
            }
          });
        }
      };
    }
  ]);

  module.directive("gnLinksBtn", [
    "gnTplResultlistLinksbtn",
    function (gnTplResultlistLinksbtn) {
      return {
        restrict: "E",
        replace: true,
        scope: true,
        templateUrl: gnTplResultlistLinksbtn
      };
    }
  ]);

  module.directive("gnMdActionsMenu", [
    "gnMetadataActions",
    "$http",
    "gnConfig",
    "gnConfigService",
    "gnGlobalSettings",
    "gnLangs",
    function (
      gnMetadataActions,
      $http,
      gnConfig,
      gnConfigService,
      gnGlobalSettings,
      gnLangs
    ) {
      return {
        restrict: "A",
        replace: true,
        templateUrl: "../../catalog/views/default/directives/partials/mdactionmenu.html",
        link: function linkFn(scope, element, attrs) {
          scope.mdService = gnMetadataActions;
          scope.md = scope.$eval(attrs.gnMdActionsMenu);
          scope.formatterList = gnGlobalSettings.gnCfg.mods.search.downloadFormatter;

          scope.tasks = [];
          scope.hasVisibletasks = false;

          gnConfigService.load().then(function (c) {
            scope.isMdWorkflowEnable = gnConfig["metadata.workflow.enable"];

            scope.isMdWorkflowAssistEnable =
              gnGlobalSettings.gnCfg.mods.workflowHelper.enabled;
            scope.workFlowApps =
              gnGlobalSettings.gnCfg.mods.workflowHelper.workflowAssistApps;
            scope.iso2Lang = gnLangs.getIso2Lang(gnLangs.getCurrent());
          });

          scope.status = undefined;

          scope.buildFormatter = function (url, uuid, isDraft) {
            if (url.indexOf("${uuid}") !== -1) {
              return url.replace("${lang}", scope.lang).replace("${uuid}", uuid);
            } else {
              return (
                "../api/records/" +
                uuid +
                url.replace("${lang}", scope.lang) +
                (isDraft == "y"
                  ? (url.indexOf("?") !== -1 ? "&" : "?") + "approved=false"
                  : "")
              );
            }
          };

          function loadWorkflowStatus() {
            return $http
              .get("../api/status/workflow", { cache: true })
              .then(function (response) {
                scope.status = {};
                response.data.forEach(function (s) {
                  scope.status[s.name] = s.id;
                });

                scope.statusEffects = {
                  editor: [
                    {
                      from: "draft",
                      to: "submitted"
                    },
                    {
                      from: "retired",
                      to: "draft"
                    },
                    {
                      from: "submitted",
                      to: "draft"
                    }
                  ],
                  reviewer: [
                    {
                      from: "draft",
                      to: "submitted"
                    },
                    {
                      from: "submitted",
                      to: "approved"
                    },
                    {
                      from: "submitted",
                      to: "draft"
                    },
                    {
                      from: "draft",
                      to: "approved"
                    },
                    {
                      from: "approved",
                      to: "retired"
                    },
                    {
                      from: "retired",
                      to: "draft"
                    }
                  ]
                };
              });
          }

          function loadTasks() {
            return $http
              .get("../api/status/task", { cache: true })
              .then(function (response) {
                scope.tasks = response.data;
                scope.getVisibleTasks();
              });
          }

          scope.getVisibleTasks = function () {
            $.each(scope.tasks, function (i, t) {
              scope.hasVisibletasks =
                scope.taskConfiguration[t.name] &&
                scope.taskConfiguration[t.name].isVisible &&
                scope.taskConfiguration[t.name].isVisible();
            });
          };

          scope.taskConfiguration = {
            doiCreationTask: {
              isVisible: function (md) {
                return gnConfig["system.publication.doi.doienabled"];
              },
              isApplicable: function (md) {
                // TODO: Would be good to return why a task is not applicable as tooltip
                // TODO: Add has DOI already
                return (
                  md &&
                  md.isPublished() &&
                  md.isTemplate === "n" &&
                  JSON.parse(md.isHarvested) === false
                );
              }
            }
          };

          /**
           * Display the publication / un-publication option. Checks:
           *   - User can review the metadata.
           *   - It's not a draft.
           *   - Retired metadata can't be published.
           *   - The user profile can publish / unpublish the metadata.
           * @param md
           * @param user
           * @returns {*|boolean|false|boolean}
           */
          scope.displayPublicationOption = function (md, user, pubOption) {
            return (
              md.canReview &&
              md.draft != "y" &&
              md.mdStatus != 3 &&
              ((md.isPublished(pubOption) && user.canUnpublishMetadata()) ||
                (!md.isPublished(pubOption) && user.canPublishMetadata()))
            );
          };

          function loadUserGroups(userId) {
            return $http
              .get("../api/users/" + userId + "/groups")
              .then(function (response) {
                scope.userGroups = response.data;
              });
          }

          // Load users groups once the scope.user.id is set.
          var loadUserGroupsUnWatch = scope.$watch("user.id", function (newUserId) {
            if (newUserId !== undefined && newUserId !== null) {
              // Call your function here with the updated user ID value
              loadUserGroups(scope.user.id);

              // Unregister the watch to avoid unnecessary callbacks
              loadUserGroupsUnWatch();
            }
          });

          /**
           * Display the helper App Groups Checks:
           *   - User is part of the appExpressionAllowed
           *   - Metadata is part of the same group
           * @param md
           * @param user user object to used to check group profile
           * @param appExpressionAllowed groups and profiles expression to be checked. The expression should be in the following format.
           *     {group}:{profile}:{object}
           *     Group - can be a regular expression defaults to all
           *     Profile/Role - profile to be used, defaults to all
           *     Object can be U (User) or M (Metadata Record)- defaults to user
           *     i.e.
           *         "" or ":" or "::"   all users, all groups for the user.
           *         ":Editor"           all users who are editor or more.
           *         "Sample"            all users who are part of the sample group.
           *         "S.*:Editor:U"      all users who are and editor for group starting with S.
           *         "S.*::M"            all metadata belonging to groups starting with S.
           *         ":Editor:M"         all metadata where current users has editor permissions.
           *         "S.*:Editor:M"      all metadata belonging to groups starting with S and user is an editor for the group.
           * @returns {*|boolean|false|boolean}
           */
          var workflowAssistAppExpressionCache = {};
          scope.isWorkflowAssistAppExpressionAllowed = function (
            md,
            user,
            appExpressionAllowed
          ) {
            // If there is no expression then return true;
            if (!appExpressionAllowed) return true;

            // If user groups not loaded then we are not ready.
            if (!scope.userGroups) return false;

            // Create a unique key based on function arguments
            var cacheKey = md.id + "-" + user.id + "-" + appExpressionAllowed;

            // Check if the value is already in the cache
            if (workflowAssistAppExpressionCache[cacheKey] !== undefined) {
              return workflowAssistAppExpressionCache[cacheKey];
            }

            var result = false;
            // Split the appExpressionAllowed into an array
            var appExpressionAllowedArray = appExpressionAllowed.split(",");

            // Loop through each allowed group regex pattern
            for (var j = 0; j < appExpressionAllowedArray.length; j++) {
              var appExpression = appExpressionAllowedArray[j];

              var appExpressionArray = appExpression.split(":");
              var appExpressionObj = {
                groupExpression: appExpressionArray[0],
                profile: appExpressionArray[1]
                  ? appExpressionArray[1][0].toUpperCase() +
                    appExpressionArray[1].substring(1).toLowerCase()
                  : appExpressionArray[1],
                type:
                  appExpressionArray[2] && appExpressionArray[2].toUpperCase() === "M"
                    ? "M"
                    : "U"
              };

              var checkMetadataProfile = function (profile) {
                return (
                  ["Editor", "Reviewer", "Administrator"].includes(profile) &&
                  ((["Editor", "Administrator"].includes(profile) && md.edit) ||
                    (["Reviewer", "Administrator"].includes(profile) && md.canReview))
                );
              };

              // If checking if no group expression and not profile.
              if (!appExpressionObj.groupExpression && !appExpressionObj.profile) {
                result = true;
              } else if (
                // If there is no group expression but there is a profile then check if the user/metadata has that profile privilege.
                !appExpressionObj.groupExpression &&
                appExpressionObj.profile
              ) {
                if (appExpressionObj.type === "M") {
                  if (appExpressionObj.profile) {
                    if (checkMetadataProfile(appExpressionObj.profile)) {
                      result = true;
                    } else {
                      console.log(
                        'User does not have metadata access "' +
                          appExpressionObj.profile +
                          '" access for group pattern "' +
                          appExpression +
                          '"'
                      );
                    }
                  }
                } else {
                  // Create profile check function name
                  var fnNameOrMore =
                    appExpressionObj.profile !== ""
                      ? "is" + appExpressionObj.profile + "OrMore"
                      : "";

                  // If the current user has the current profile then skip this entry and continue to the next one.
                  if (
                    angular.isFunction(user[fnNameOrMore]) ? user[fnNameOrMore]() : false
                  ) {
                    result = true;
                  } else {
                    console.log(
                      'User does not have profile "' +
                        appExpressionObj.profile +
                        '" access for group pattern "' +
                        appExpression +
                        '"'
                    );
                  }
                }
              } else {
                // There is a group expression so lets parse it.
                var matchedGroupExpression = [];
                if (appExpressionObj.groupExpression) {
                  // Loop through each user group
                  for (var i = 0; i < scope.userGroups.length; i++) {
                    var userGroup = scope.userGroups[i];

                    // Create a regular expression object from the pattern
                    var regex = new RegExp(appExpressionObj.groupExpression);

                    // Check if there is a match
                    var match = regex.exec(userGroup.group.name);

                    if (match) {
                      console.log(
                        'User profile group "' +
                          userGroup.group.name +
                          "." +
                          userGroup.user.profile +
                          '" matches allowed group pattern "' +
                          appExpression +
                          '"'
                      );

                      matchedGroupExpression.push({
                        groupName: userGroup.group.name,
                        groupId: userGroup.group.id
                      });
                    }
                  }
                }

                if (
                  // If checking users and there is no group expression but there is a profile then check if the user has that profile.
                  appExpressionObj.groupExpression &&
                  !appExpressionObj.profile
                ) {
                  if (appExpressionObj.type === "M") {
                    if (
                      matchedGroupExpression.some((obj) => obj.groupId === md.groupOwner)
                    ) {
                      result = true;
                    } else {
                      console.log(
                        'User does not have metadata group "' +
                          md.groupOwner +
                          '" access for group pattern "' +
                          appExpression +
                          '"'
                      );
                    }
                  } else {
                    if (matchedGroupExpression.length !== 0) {
                      result = true;
                    } else {
                      console.log(
                        'User does not have group for group pattern "' +
                          appExpression +
                          '"'
                      );
                    }
                  }
                } else {
                  if (appExpressionObj.type === "M") {
                    if (
                      checkMetadataProfile(appExpressionObj.profile) &&
                      matchedGroupExpression.some((obj) => obj.groupId === md.groupOwner)
                    ) {
                      result = true;
                    } else {
                      console.log(
                        'User does not have profile access to groups in group pattern "' +
                          appExpression +
                          '"'
                      );
                    }
                  } else {
                    // We have a group and profile in the expression.
                    var found = false;
                    for (var i = 0; i < matchedGroupExpression.length; i++) {
                      // Create profile check function name
                      var fnNameForGroup =
                        appExpressionObj.profile !== ""
                          ? "is" + appExpressionObj.profile + "ForGroup"
                          : "";

                      // If the current user has the current profile then skip this entry and continue to the next one.
                      // If the group function exist then admins automatically pass.
                      if (
                        angular.isFunction(user[fnNameForGroup])
                          ? user.isAdmin() ||
                            user[fnNameForGroup](matchedGroupExpression[i].groupId)
                          : false
                      ) {
                        found = true;
                        break;
                      }
                    }
                    if (found) {
                      result = true;
                    } else {
                      console.log(
                        'User does not have profile access to groups in group pattern "' +
                          appExpression +
                          '"'
                      );
                    }
                  }
                }
              }
            }
            workflowAssistAppExpressionCache[cacheKey] = result;
            return result;
          };

          loadTasks();
          loadWorkflowStatus();

          scope.$watch(attrs.gnMdActionsMenu, function (a) {
            scope.md = a;
          });

          scope.getScope = function () {
            return scope;
          };
        }
      };
    }
  ]);

  module.directive("gnPeriodChooser", [
    function () {
      return {
        restrict: "A",
        replace: true,
        templateUrl:
          "../../catalog/views/default/directives/" + "partials/periodchooser.html",
        scope: {
          label: "@gnPeriodChooser",
          dateFrom: "=",
          dateTo: "="
        },
        link: function linkFn(scope, element, attr) {
          var today = moment();
          scope.format = "YYYY-MM-DD";
          scope.options = [
            "today",
            "yesterday",
            "thisWeek",
            "thisMonth",
            "last3Months",
            "last6Months",
            "thisYear"
          ];
          scope.setPeriod = function (option) {
            if (option === "today") {
              var date = today.format(scope.format);
              scope.dateFrom = date;
            } else if (option === "yesterday") {
              var date = today.clone().subtract(1, "day").format(scope.format);
              scope.dateFrom = date;
              scope.dateTo = today.format(scope.format);
              return;
            } else if (option === "thisWeek") {
              scope.dateFrom = today.clone().startOf("week").format(scope.format);
            } else if (option === "thisMonth") {
              scope.dateFrom = today.clone().startOf("month").format(scope.format);
            } else if (option === "last3Months") {
              scope.dateFrom = today
                .clone()
                .startOf("month")
                .subtract(3, "month")
                .format(scope.format);
            } else if (option === "last6Months") {
              scope.dateFrom = today
                .clone()
                .startOf("month")
                .subtract(6, "month")
                .format(scope.format);
            } else if (option === "thisYear") {
              scope.dateFrom = today.clone().startOf("year").format(scope.format);
            }
            scope.dateTo = today.clone().add(1, "day").format(scope.format);
          };
        }
      };
    }
  ]);

  /**
   * https://www.elastic.co/guide/en/elasticsearch/reference/current/range.html
   */
  module.directive("gnDateRangeFilter", [
    function () {
      return {
        restrict: "A",
        replace: true,
        templateUrl:
          "../../catalog/views/default/directives/" + "partials/dateRangeFilter.html",
        scope: {
          label: "@gnDateRangeFilter",
          field: "=",
          fieldName: "="
        },
        link: function linkFn(scope, element, attr) {
          var today = moment();
          scope.relations = ["intersects", "within", "contains"];
          scope.relation = scope.relations[0];
          scope.field.range = scope.field.range || {};
          scope.field.range[scope.fieldName] = {
            gte: null,
            lte: null,
            relation: scope.relation
          };

          scope.setRange = function () {
            scope.field.range[scope.fieldName].gte = scope.dateFrom;
            scope.field.range[scope.fieldName].lte = scope.dateTo;
            scope.field.range[scope.fieldName].relation = scope.relation;
          };

          scope.format = "YYYY-MM-DD";
          scope.options = [
            "today",
            "yesterday",
            "thisWeek",
            "thisMonth",
            "last3Months",
            "last6Months",
            "thisYear"
          ];
          scope.setPeriod = function (option) {
            if (option === "today") {
              var date = today.format(scope.format);
              scope.dateFrom = date;
            } else if (option === "yesterday") {
              var date = today.clone().subtract(1, "day").format(scope.format);
              scope.dateFrom = date;
              scope.dateTo = today.format(scope.format);
              return;
            } else if (option === "thisWeek") {
              scope.dateFrom = today.clone().startOf("week").format(scope.format);
            } else if (option === "thisMonth") {
              scope.dateFrom = today.clone().startOf("month").format(scope.format);
            } else if (option === "last3Months") {
              scope.dateFrom = today
                .clone()
                .startOf("month")
                .subtract(3, "month")
                .format(scope.format);
            } else if (option === "last6Months") {
              scope.dateFrom = today
                .clone()
                .startOf("month")
                .subtract(6, "month")
                .format(scope.format);
            } else if (option === "thisYear") {
              scope.dateFrom = today.clone().startOf("year").format(scope.format);
            }
            scope.dateTo = today.clone().add(1, "day").format(scope.format);
            scope.setRange();
          };
          scope.$watch("dateFrom", function (n, o) {
            if (n !== o) {
              scope.setRange();
            }
          });
          scope.$watch("dateTo", function (n, o) {
            if (n !== o) {
              scope.setRange();
            }
          });

          scope.$on("beforeSearchReset", function () {
            scope.dateFrom = null;
            scope.dateTo = null;
            scope.relation = scope.relations[0];
          });
        }
      };
    }
  ]);
})();
