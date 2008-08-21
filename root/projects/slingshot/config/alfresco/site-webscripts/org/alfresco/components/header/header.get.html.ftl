<#assign siteActive><#if page.url.templateArgs.site??>true<#else>false</#if></#assign>
<#assign isGuest=(user.name == 'guest') />
<#if (! isGuest)>
<script type="text/javascript">//<![CDATA[
   var thisHeader = new Alfresco.Header("${args.htmlid}").setOptions({
      siteId: "${page.url.templateArgs.site!""}",
      searchType: "${page.url.templateArgs.site!'all'}" // default search type
   }).setMessages(
      ${messages}
   );
//]]></script>
</#if>

<div class="header">
   <div class="logo-wrapper">
      <div class="logo">
         <img src="${url.context}/themes/${theme}/images/app-logo.png" alt="Alfresco Share" />
      </div>
   </div>
   

   <div class="menu-wrapper">
      <#if (! isGuest)>
      <div class="personal-menu">
         <span class="menu-item-icon my-dashboard"><a href="${url.context}/page/user/${user.name}/dashboard">${msg("link.myDashboard")}</a></span>
         <span class="menu-item-icon my-profile"><a href="${url.context}/page/user/${user.name}/profile">${msg("link.myProfile")}</a></span>
         <span class="menu-item-icon sites"><a href="${url.context}/page/site-finder">${msg("link.sites")}</a></span>
      </div>
      </#if>

      <div class="util-menu" id="${args.htmlid}-searchcontainer">
         <span class="menu-item"><a href="#">${msg("link.help")}</a></span>
         <#if (! isGuest)>
         <span class="menu-item-separator">|</span>
         <span class="menu-item"><a href="${url.context}/logout">${msg("link.logout")} (${user.name})</a></span>
         <span class="menu-item-separator">|</span>
         <span class="menu-item">
            <span class="search-container">
               <label for="${args.htmlid}-searchtext" style="display:none">${msg("header.search.inputlabel")}</label>
               <input type="text" class="search-tinput" name="${args.htmlid}-searchtext" id="${args.htmlid}-searchtext" value="" />
               <span id="${args.htmlid}-search-tbutton" class="search-site-icon">&nbsp;</span>
            </span>
         </span>
         </#if>
      </div>
   </div>

   <#if (! isGuest)>
   <div id="${args.htmlid}-searchtogglemenu" class="searchtoggle hidden">
      <div class="bd">
         <ul>
            <li class="searchtoggleitem">
               <a class="searchtoggleitemlabel<#if siteActive == 'false'> disabled</#if>" href="<#if siteActive == 'false'>#<#else>javascript:thisHeader.doToggleSearchType('site')</#if>">${msg("header.search.searchsite", page.url.templateArgs.site!"")}</a>
            </li>
            <li class="searchtoggleitem"><a class="searchtoggleitemlabel" href="javascript:thisHeader.doToggleSearchType('all')">${msg("header.search.searchall")}</a></li>
         </ul>            
      </div>
   </div>	
   </#if>
</div>
