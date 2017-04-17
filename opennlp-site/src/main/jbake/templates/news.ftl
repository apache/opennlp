<#include "header.ftl">
	<#include "menu.ftl">

      <div class="row-fluid marketing">
        <div class="span9">
          <h2>News</h2>
          <ul>
          	<#list posts as post>
          		<#if (post.status == "published") && (post.category??) && (post.category == "news")>
          			<li><p>${post.date?string("yyyy-MM-dd")}: <a href="/${post.uri}">${post.title}</a></p></li>
          		</#if>
          	</#list>
          </ul>
          <p></p>
          <p>Subscribe to Apache OpenNLP updates using the <a href="/${config.feed_file}">feed</a>.</p>
        </div>        
      </div>

      <hr>

<#include "footer.ftl">
