define([
  'service/Rest',
  'ui/UIBean',
  'ui/UITable',
  'site/scribengin/UIDataflowConfig',
  'site/scribengin/UIDataflowReport',
], function(Rest, UIBean, UITable, UIDataflowConfig, UIDataflowReport) {
  var UIListDataflow = UITable.extend({
    label: "List Dataflow",

    config: {
      toolbar: {
        dflt: {
          actions: [
            {
              action: "onReload", icon: "refresh", label: "Refresh", 
              onClick: function(thisTable) { 
                console.log("call onReload");
              } 
            }
          ]
        }
      },
      
      bean: {
        label: 'List Dataflow',
        fields: [
          { 
            field: "id",   label: "Dataflow Id", defaultValue: '', toggled: true, filterable: true,
            onClick: function(thisTable, row) {
              var dataflowDescriptor = thisTable.getItemOnCurrentPage(row) ;
              var uiBreadcumbs = thisTable.getAncestorOfType('UIBreadcumbs') ;
              uiBreadcumbs.push(new UIDataflowReport({ dataflowDescriptor: dataflowDescriptor })) ;
            }
          },
          { 
            field: "numOfMaster",   label: "Masters", defaultValue: '', toggled: true, filterable: true,
            custom: {
              getDisplay: function(bean) { return bean.master.numOfInstances ; },
            }
          },
          { 
            field: "numOfWorkers",   label: "Workers", defaultValue: '', toggled: true, filterable: true,
            custom: {
              getDisplay: function(bean) { return bean.worker.numOfInstances ; },
            }
          }
        ],
        actions:[
          {
            icon: "config", label: "Config",
            onClick: function(thisTable, row) { 
              var dataflowDescriptor = thisTable.getItemOnCurrentPage(row) ;
              var uiBreadcumbs = thisTable.getAncestorOfType('UIBreadcumbs') ;
              uiBreadcumbs.push(new UIDataflowConfig({ dataflowDescriptor: dataflowDescriptor})) ;
            }
          },
          {
            icon: "stop", label: "Stop",
            onClick: function(thisTable, row) { 
              var dataflowDescriptor = thisTable.getItemOnCurrentPage(row) ;
              Rest.dataflow.stop(dataflowDescriptor.id);
            }
          },
          {
            icon: "resume", label: "Resume",
            onClick: function(thisTable, row) { 
              var dataflowDescriptor = thisTable.getItemOnCurrentPage(row) ;
              Rest.dataflow.resume(dataflowDescriptor.id);
            }
          }
        ]
      }
    },

    initActive: function() {
      this.label = "List Active Dataflow";
      var dataflowList = Rest.dataflow.getActiveDataflows();
      this.setBeans(dataflowList) ;
    },

    initHistory: function() {
      this.label = "List History Dataflow";
      var dataflowList = Rest.dataflow.getHistoryDataflows();
      this.setBeans(dataflowList) ;
    }
    
  });
  
  return UIListDataflow ;
});
