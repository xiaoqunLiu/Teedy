angular.module('docs').controller('RegistrationRequestsController', ['$scope', '$dialog', '$translate', 'Restangular', function($scope, $dialog, $translate, Restangular) {
    // Load registration requests
    $scope.loadRequests = function() {
        Restangular.one('registration/list').get().then(function(data) {
            $scope.requests = data.requests;
            // Explicitly refresh translations after loading data
            $translate.refresh();
        });
    };

    // Approve a registration request
    $scope.approveRequest = function(request) {
        $dialog.messageBox($translate.instant('admin.registration_requests.approve_title'),
            $translate.instant('admin.registration_requests.approve_message', { username: request.username }),
            [{
                label: $translate.instant('ok'),
                primary: true
            }, {
                label: $translate.instant('cancel')
            }])
            .then(function() {
                Restangular.one('registration', request.id).post('approve').then(function() {
                    $scope.loadRequests();
                });
            });
    };

    // Reject a registration request
    $scope.rejectRequest = function(request) {
        $dialog.messageBox($translate.instant('admin.registration_requests.reject_title'),
            '<form class="form-horizontal">' +
            '<div class="form-group">' +
            '<label class="col-sm-3 control-label">' + $translate.instant('admin.registration_requests.rejection_reason') + '</label>' +
            '<div class="col-sm-9">' +
            '<input type="text" class="form-control" ng-model="rejectionReason" required>' +
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
                Restangular.one('registration', request.id).post('reject', {
                    reason: $scope.rejectionReason
                }).then(function() {
                    $scope.loadRequests();
                });
            });
    };

    // Load requests on page load
    $scope.loadRequests();
}]); 