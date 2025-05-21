.when('/admin/registration-requests', {
    templateUrl: 'partial/admin/registration-requests.html',
    controller: 'RegistrationRequestsController',
    resolve: {
        auth: ['$q', 'BaseFunction', function($q, BaseFunction) {
            return BaseFunction.checkBaseFunction('ADMIN');
        }]
    }
}) 