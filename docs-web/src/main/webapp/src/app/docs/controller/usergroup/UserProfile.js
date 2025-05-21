'use strict';

/**
 * User profile controller.
 */
angular.module('docs').controller('UserProfileController', ['$scope', '$stateParams', '$dialog', '$translate', 'Restangular', function($scope, $stateParams, $dialog, $translate, Restangular) {
  console.log('UserProfileController initialized for username:', $stateParams.username);

  // Load user
  Restangular.one('user', $stateParams.username).get().then(function(data) {
    $scope.user = data;
  });

  // Request registration
  $scope.requestRegistration = function() {
    console.log('requestRegistration function called');
    $dialog.messageBox($translate.instant('user.profile.request_registration'), 
      $translate.instant('user.profile.request_registration_message'), 
      [{
        label: $translate.instant('ok'),
        primary: true
      }, {
        label: $translate.instant('cancel')
      }])
      .then(function() {
        console.log('Confirmation dialog closed with OK');
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
            console.log('Registration form dialog closed with OK');
            // Submit the registration request
            Restangular.one('registration').post('', {
              username: $scope.username,
              email: $scope.email,
              password: $scope.password
            }).then(function() {
              console.log('Registration request submitted successfully');
              $dialog.messageBox($translate.instant('user.profile.request_registration_success_title'),
                $translate.instant('user.profile.request_registration_success_message'),
                [{
                  label: $translate.instant('ok'),
                  primary: true
                }]);
            }, function(error) {
              console.error('Error submitting registration request:', error);
              // Optionally show an error message dialog here
              $dialog.messageBox('Error', 'An error occurred while submitting your registration request.', [{
                label: $translate.instant('ok'),
                primary: true
              }]);
            });
          }, function() {
            console.log('Registration form dialog closed with Cancel');
          });
      }, function() {
        console.log('Confirmation dialog closed with Cancel');
      });
  };
}]);