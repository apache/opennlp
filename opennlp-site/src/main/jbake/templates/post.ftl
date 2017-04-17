<#include "header.ftl">
	<#include "menu.ftl">
	
	<div class="row-fluid marketing">
		<div class="span12">
			<p>${content.body}</p>
			<p><em>${content.date?string("dd MMMM yyyy")}</em></p>
			<div id="share"><#include "share_links.ftl"></div>
        </div>
	</div>

	<hr>
<#include "footer.ftl">
