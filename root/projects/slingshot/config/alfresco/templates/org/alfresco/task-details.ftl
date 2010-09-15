<#include "include/alfresco-template.ftl" />
<@templateHeader />

<@templateBody>
   <div id="alf-hd">
      <@region id=appType + "header" scope="global" protected=true />
      <@region id=appType + referrer + "title" scope="template" protected=true />
      <@region id=appType + referrer + "navigation" scope="template" protected=true />
   </div>
   <div id="bd">
      <div class="share-form">
         <@region id="task-header" scope="template" />
         <@region id="task-form" scope="template" />
         <@region id="task-actions" scope="template" />
      </div>
   </div>
</@>

<@templateFooter>
   <div id="alf-ft">
      <@region id="footer" scope="global" protected=true />
      <@region id="data-loader" scope="template" />
   </div>
</@>
