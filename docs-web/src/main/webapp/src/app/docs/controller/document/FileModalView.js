'use strict';

/**
 * File modal view controller.
 */
angular.module('docs').controller('FileModalView', function ($uibModal, $uibModalInstance, $scope, $state, $stateParams, $sce, Restangular, $transitions) {
  var setFile = function (files) {
    // Search current file
    _.each(files, function (value) {
      if (value.id === $stateParams.fileId) {
        $scope.file = value;
        $scope.trustedFileUrl = $sce.trustAsResourceUrl('../api/file/' + $stateParams.fileId + '/data');
      }
    });
  };

  // Load files
  Restangular.one('file/list').get({ id: $stateParams.id }).then(function (data) {
    $scope.files = data.files;
    setFile(data.files);

    // File not found, maybe it's a version
    if (!$scope.file) {
      Restangular.one('file/' + $stateParams.fileId + '/versions').get().then(function (data) {
        setFile(data.files);
      });
    }
  });

  /**
   * Return the next file.
   */
  $scope.nextFile = function () {
    var next = undefined;
    _.each($scope.files, function (value, key) {
      if (value.id === $stateParams.fileId) {
        next = $scope.files[key + 1];
      }
    });
    return next;
  };

  /**
   * Return the previous file.
   */
  $scope.previousFile = function () {
    var previous = undefined;
    _.each($scope.files, function (value, key) {
      if (value.id === $stateParams.fileId) {
        previous = $scope.files[key - 1];
      }
    });
    return previous;
  };

  /**
   * Navigate to the next file.
   */
  $scope.goNextFile = function () {
    var next = $scope.nextFile();
    if (next) {
      $state.go('^.file', { id: $stateParams.id, fileId: next.id });
    }
  };

  /**
   * Navigate to the previous file.
   */
  $scope.goPreviousFile = function () {
    var previous = $scope.previousFile();
    if (previous) {
      $state.go('^.file', { id: $stateParams.id, fileId: previous.id });
    }
  };

  /**
   * Open the file in a new window.
   */
  $scope.openFile = function () {
    window.open('../api/file/' + $stateParams.fileId + '/data');
  };

  /**
   * Open the file content a new window.
   */
  $scope.openFileContent = function () {
    window.open('../api/file/' + $stateParams.fileId + '/data?size=content');
  };

  /**
   * Print the file.
   */
  $scope.printFile = function () {
    var popup = window.open('../api/file/' + $stateParams.fileId + '/data', '_blank');
    popup.onload = function () {
      popup.print();
      popup.close();
    }
  };

  /**
   * Close the file preview.
   */
  $scope.closeFile = function () {
    $uibModalInstance.dismiss();
  };

  // Close the modal when the user exits this state
  var off = $transitions.onStart({}, function(transition) {
    if (!$uibModalInstance.closed) {
      if (transition.to().name === $state.current.name) {
        $uibModalInstance.close();
      } else {
        $uibModalInstance.dismiss();
      }
    }
    off();
  });

  /**
   * Return true if we can display the preview image.
   */
  $scope.canDisplayPreview = function () {
    return $scope.file && $scope.file.mimetype !== 'application/pdf';
  };

  // 语言列表
  $scope.langList = [
    { code: 'zh-CHS', name: '中文' },
    { code: 'en', name: 'English' },
    { code: 'fr', name: 'Français' }
  ];
  $scope.targetLang = $scope.langList[0].code;

  // 使用$uibModal弹出语言选择框
  $scope.translateFile = function () {
    $uibModal.open({
      templateUrl: 'partial/docs/file.translate.modal.html',
      controller: function($scope, $uibModalInstance, langList, targetLang) {
        $scope.langList = langList;
        $scope.targetLang = targetLang;
        $scope.ok = function() {
          $uibModalInstance.close($scope.targetLang);
        };
        $scope.cancel = function() {
          $uibModalInstance.dismiss();
        };
      },
      resolve: {
        langList: function() { return $scope.langList; },
        targetLang: function() { return $scope.targetLang; }
      }
    }).result.then(function(selectedLang) {
      $scope.targetLang = selectedLang;
      $scope.doTranslate();
    });
  };

  // 执行翻译
  $scope.doTranslate = function () {
    // 这里调用后端API进行翻译
    Restangular.one('file/' + $stateParams.fileId + '/translate').post('', {
      to: $scope.targetLang
    }).then(function (resp) {
      alert('翻译成功，已生成新文件！');
      $scope.refreshFiles(); // 翻译成功后自动刷新文件列表
    }, function (err) {
      alert('翻译失败，请重试！');
    });
  };

  $scope.refreshFiles = function () {
    Restangular.one('file/list').get({ id: $stateParams.id }).then(function (data) {
      $scope.files = data.files;
      setFile(data.files);
    });
  };
});