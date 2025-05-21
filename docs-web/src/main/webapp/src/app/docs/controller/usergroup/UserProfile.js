'use strict';

/**
 * User profile controller.
 */
angular.module('docs').controller('UserProfileController', ['$scope', '$dialog', '$translate', 'Restangular', function($scope, $dialog, $translate, Restangular) {
  // Load user
  Restangular.one('user', $stateParams.username).get().then(function(data) {
    $scope.user = data;
  });

  // Request registration
  $scope.requestRegistration = function() {
    $dialog.messageBox($translate.instant('user.profile.request_registration'), 
      $translate.instant('user.profile.request_registration_message'), 
      [{
        label: $translate.instant('ok'),
        primary: true
      }, {
        label: $translate.instant('cancel')
      }])
      .then(function() {
        // Show registration form dialog
        $dialog.messageBox($translate.instant('user.profile.request_registration'), 
          '<form class="form-horizontal">' +
          '<div class="form-group">' +
          '<label class="col-sm-3 control-label">' + $translate.instant('user.profile.username') + '</label>' +
          '<div class="col-sm-9">' +
          '<input type="text" class="form-control" ng-model="username" required>' +
          '</div>' +
          '</div>' +
          '<div class="form-group">' +
          '<label class="col-sm-3 control-label">' + $translate.instant('user.profile.email') + '</label>' +
          '<div class="col-sm-9">' +
          '<input type="email" class="form-control" ng-model="email" required>' +
          '</div>' +
          '</div>' +
          '<div class="form-group">' +
          '<label class="col-sm-3 control-label">' + $translate.instant('user.profile.password') + '</label>' +
          '<div class="col-sm-9">' +
          '<input type="password" class="form-control" ng-model="password" required>' +
          '</div>' +
          '</div>' +
          '</form>', 
          [{
            label: $translate.instant('ok'),
            primary: true
          }, {
            label: $translate.instant('cancel')
          }])
          .then(function() {
            // Submit the registration request
            Restangular.one('registration').post('', {
              username: $scope.username,
              email: $scope.email,
              password: $scope.password
            }).then(function() {
              $dialog.messageBox($translate.instant('user.profile.request_registration_success_title'),
                $translate.instant('user.profile.request_registration_success_message'),
                [{
                  label: $translate.instant('ok'),
                  primary: true
                }]);
            });
          });
      });
  };
}]);