import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.bc.JiraServiceContextImpl
import com.atlassian.jira.jql.parser.JqlQueryParser
import com.atlassian.jira.bc.issue.search.SearchService
import com.atlassian.jira.web.bean.PagerFilter
import com.atlassian.sal.api.ApplicationProperties
import com.atlassian.sal.api.UrlMode
import com.onresolve.scriptrunner.runner.ScriptRunnerImpl
import com.onresolve.scriptrunner.runner.rest.common.CustomEndpointDelegate
import org.apache.commons.httpclient.auth.AuthScope
import org.apache.commons.httpclient.UsernamePasswordCredentials
import org.apache.commons.httpclient.methods.*
import org.apache.commons.httpclient.HttpClient
import javax.servlet.http.HttpServletRequest
import javax.ws.rs.core.MultivaluedMap
import javax.ws.rs.core.Response
import groovy.json.JsonBuilder
import groovy.transform.BaseScript
import groovy.transform.Field

@BaseScript CustomEndpointDelegate delegate

def issueManager = ComponentAccessor.getIssueManager()
def userManager = ComponentAccessor.getUserManager()
def groupManager = ComponentAccessor.getGroupManager()
def jqlQueryParser = ComponentAccessor.getComponent(JqlQueryParser)
def searchService = ComponentAccessor.getComponent(SearchService)
def userSearchService = ComponentAccessor.getUserSearchService()
def avatarService = ComponentAccessor.getAvatarService()
def applicationProperties = ScriptRunnerImpl.getOsgiService(ApplicationProperties)
def baseUrl = applicationProperties.getBaseUrl(UrlMode.ABSOLUTE)

def API_USERNAME = '123'
def API_PASS = '123'

@Field Collection departmentsList = ['department1', 'department2', 'department2']
def managerGroup = 'managers-group'
def directorGroup = 'director-group'
def president = 'the guy'

getDirector(httpMethod: "GET", groups: ['jira-api-users']) { MultivaluedMap queryParams, body, HttpServletRequest request ->
    def username = queryParams.getFirst('username') as String
    if (username == null)
    	return Response.status(400).build()
    def groups = groupManager.getGroupNamesForUser(username)
    def usersDepartment
    def groupsDepartmentUser
    def director
    for (group in groups){ // looking for department group from user's group list
        if (departmentsList.contains(group)){ // selected group is department group
            log.warn("found department group: ${group}")
            usersDepartment = groupManager.getUserNamesInGroup(group)
            for (user in usersDepartment){ // looking for director user from department group
                groupsDepartmentUser = groupManager.getGroupNamesForUser(user)
                if (groupsDepartmentUser.contains(directorGroup)){ // selected user is director
                    director = user
                    log.warn("found director '${user}' of department '${group}'")
                    break
                }
            }
        }
    }
    if (director == null){
        log.warn("department of selected user does not have director, president ${president} returned")
        director = president
    }
    return Response.ok(new JsonBuilder(director: director).toString()).build()
}

getManager(httpMethod: "GET", groups: ['jira-api-users']) { MultivaluedMap queryParams, body, HttpServletRequest request ->
    def username = queryParams.getFirst('username') as String
    if (username == null)
    	return Response.status(400).build()
    def groups = groupManager.getGroupNamesForUser(username)
    def usersTeam
    def groupsTeamUser
    def manager
    def director
    for (group in groups){ // looking for department group from user's group list
        if (groupIsTeam(group)){ // selected group is team group
            log.warn("found team group: ${group}")
            usersTeam = groupManager.getUserNamesInGroup(group)
            for (user in usersTeam){ // looking for manager user from team group
                groupsTeamUser = groupManager.getGroupNamesForUser(user)
                if (groupsTeamUser.contains(managerGroup)){ // selected user is manager
                    manager = user
                    log.warn("found manager '${user}' of team '${group}'")
                    break
                }
            }
        }
    }
    if (manager == null){ // if manager not found use director as manager
        def client = new HttpClient();
        def method = new GetMethod("${baseUrl}/rest/scriptrunner/latest/custom/getDirector?username=${queryParams.getFirst('username') as String}")
        def credentials = new UsernamePasswordCredentials(API_USERNAME, API_PASS)
        client.getParams().setAuthenticationPreemptive(true)
        client.getState().setCredentials(AuthScope.ANY, credentials)
        client.executeMethod(method)
        manager = method.getResponseBodyAsString()
        method.releaseConnection()
        log.warn("team of selected user does not have manager, director as manager '${director}' returned")
    }
    return Response.ok(new JsonBuilder(manager: manager).toString()).build()
}

def groupIsTeam(group){
    for (department in departmentsList){
        if ((group as String).contains("${department}-") && group != 'drm-new'){
            log.warn("team group ${group}?")
            return true
        }
    }
    return false
}

class Component {
    String id
    String text
}

getAssetComponents(httpMethod: 'GET', groups: ['jira-core-users', 'jira-software-users', 'jira-servicedesk-users']) { MultivaluedMap queryParams, body ->
    def type = queryParams.getFirst('type') as String
    if(type == null){
        log.error('description of components not provided')
        return Response.status(400).build()
    }
    def project = issueManager.getIssueObject('ASSET-1').getProjectObject()  // to load available options related to project
    def componentsUnfiltered = project.getComponents()
    def componentList = []
    componentsUnfiltered.each { component ->
        if ((component['description'] as String).contains(type) || ((component['description'] as String).toLowerCase()).contains(type))
            componentList.add(new Component(id: component['name'] as String, text: component['name'] as String))
    }
    return Response.ok(componentList).build()
}

class User {
    String id
    String text
    String imgSrc
}

getActiveUsers(httpMethod: 'GET', groups: ['jira-core-users', 'jira-software-users', 'jira-servicedesk-users']) { MultivaluedMap queryParams, body ->
    def accessToken = 'dupa'
    def providedToken = queryParams.getFirst('accessToken') as String
    if (providedToken != accessToken){
        log.error("token ${providedToken} is not valid")
        return Response.status(400).build()
    }
    def loggedUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()
    def jiraServiceContext = new JiraServiceContextImpl(loggedUser)
    def users = userSearchService.findUsersAllowEmptyQuery(jiraServiceContext, "")
    def usersList = []
    for (user in users)
        if(user.isActive())
            usersList.add(new User(id: user.name, text: user.displayName, imgSrc: avatarService.getAvatarURL(loggedUser, user) as String))
    return Response.ok(new JsonBuilder(usersList).toString()).build()
}

class Access {
    String id
    String text
    String imgSrc
}

getPersonAccesses(httpMethod: 'GET', groups: ['jira-core-users', 'jira-software-users', 'jira-servicedesk-users']) { MultivaluedMap queryParams, body ->
	def accessToken = 'dupa'
    def providedToken = queryParams.getFirst('accessToken') as String
    if (providedToken != accessToken){
        log.error('token ${providedToken} is not valid')
        return Response.status(400).build()
    }
    def users = (queryParams.getFirst('username') as String)
    def usernames = users.split(',')
    def user
    def accessList = []
    for (username in usernames){
        user = userManager.getUserByName(username)
        def query = jqlQueryParser.parseQuery("issuetype = 'Access' AND 'User' = $user.name AND resolution IS empty")
        log.warn(query)
        def results = (searchService.search(userManager.getUserByName('admin'), query, PagerFilter.getUnlimitedFilter())).getResults()
        results.each { issue ->
            issue.getIssueType().getAvatar()
            if (usernames.size() > 1)
            	accessList.add(new Access(id: "${issue.key}", text: "${issue.key} ${issue.summary} (${user.displayName})", imgSrc: issue.getIssueType().getIconUrl()))
            else
                accessList.add(new Access(id: "${issue.key}", text: "${issue.key} ${issue.summary}", imgSrc: issue.getIssueType().getIconUrl()))
        }
    }

    return Response.ok(new JsonBuilder(accessList).toString()).build()
}