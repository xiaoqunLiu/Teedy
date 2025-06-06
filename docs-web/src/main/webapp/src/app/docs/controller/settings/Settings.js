'use strict';

/**
 * Settings controller.
 */
angular.module('docs').controller('Settings', function($scope, User) {
  // Flag if the user is admin
  User.userInfo().then(function(data) {
    $scope.isAdmin = data.base_functions.indexOf('ADMIN') !== -1;
    $scope.hasBaseFunction = function(func) {
      return data.base_functions.indexOf(func) !== -1;
    };
  });
});