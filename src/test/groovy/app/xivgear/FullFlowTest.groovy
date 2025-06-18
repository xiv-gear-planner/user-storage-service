package app.xivgear

import app.xivgear.userstorage.dto.*
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.netty.DefaultHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.token.jwt.generator.JwtTokenGenerator
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.junit.jupiter.api.Test

import java.security.SecureRandom

import static org.junit.jupiter.api.Assertions.*

@MicronautTest
@CompileStatic
@Slf4j
class FullFlowTest {

	@Singleton
	@Inject
	EmbeddedServer server

	@Inject
	@Client
	DefaultHttpClient client

	@Inject
	JwtTokenGenerator tokenGenerator

	private final int UID = new SecureRandom().nextInt 1_000_000, Integer.MAX_VALUE

	private String validToken

	@PostConstruct
	void configureClient() {
		log.info "Generated UID: ${UID}"
		client.configuration.exceptionOnErrorStatus = false
		Authentication auth = Authentication.build(
				UID.toString(),
				['verified'],
				[userId: UID] as Map<String, Object>
		)
		validToken = tokenGenerator.generateToken(auth, 30 * 60).orElseThrow()
	}

	void addHeaders(MutableHttpRequest<?> req, String token = validToken) {
		req.header 'Authorization', "Bearer ${token}"
		req.header 'xivgear-csrf', '1'
	}

	@Test
	void testFullFlow() {
		// Should start with no preferences
		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("userdata/preferences")).tap {
				addHeaders it
			}
			HttpResponse<GetPreferencesResponse> response = client.toBlocking().exchange req, GetPreferencesResponse
			assertEquals HttpStatus.OK, response.status
			assertFalse response.body().found
			assertNull response.body().preferences
		}

		{
			var prefs = new UserPreferences().tap {
				lightMode = true
				languageOverride = 'foo'
			}
			var prefsReq = new PutPreferencesRequest().tap {
				preferences = prefs
			}

			HttpRequest<PutPreferencesRequest> req = HttpRequest.PUT(server.URI.resolve("userdata/preferences"), prefsReq).tap {
				addHeaders it
			}
			HttpResponse<?> response = client.toBlocking().exchange req
			assertEquals HttpStatus.OK, response.status
		}

		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("userdata/preferences")).tap {
				addHeaders it
			}

			HttpResponse<GetPreferencesResponse> response = client.toBlocking().exchange req, GetPreferencesResponse
			assertEquals HttpStatus.OK, response.status
			assertTrue response.body().found
			assertEquals true, response.body().preferences.lightMode
			assertEquals 'foo', response.body().preferences.languageOverride
		}

		{
			var prefs = new UserPreferences().tap {
				lightMode = false
				languageOverride = 'bar'
			}
			var prefsReq = new PutPreferencesRequest().tap {
				preferences = prefs
			}

			HttpRequest<PutPreferencesRequest> req = HttpRequest.PUT(server.URI.resolve("userdata/preferences"), prefsReq).tap {
				addHeaders it
			}
			HttpResponse<?> response = client.toBlocking().exchange req
			assertEquals HttpStatus.OK, response.status
		}

		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("userdata/preferences")).tap {
				addHeaders it
			}

			HttpResponse<GetPreferencesResponse> response = client.toBlocking().exchange req, GetPreferencesResponse
			assertEquals HttpStatus.OK, response.status
			assertTrue response.body().found
			assertEquals false, response.body().preferences.lightMode
			assertEquals 'bar', response.body().preferences.languageOverride
		}

		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("userdata/sheets")).tap {
				addHeaders it
			}

			HttpResponse<GetSheetsResponse> response = client.toBlocking().exchange req, GetSheetsResponse
			assertEquals HttpStatus.OK, response.status
//			assertTrue response.body().sheets.isEmpty()
		}

		String sheetKey = 'sheet-save-123-foobar';
		// Upload a new sheet
		{
			var sheetReq = new PutSheetRequest().tap {
				sheetName = 'Test Sheet'
				sheetData = [foo: 'bar']
				lastSyncedVersion = 5
				newSheetVersion = 6
			}

			HttpRequest<PutSheetRequest> req = HttpRequest.PUT(server.URI.resolve("userdata/sheets/${sheetKey}"), sheetReq).tap {
				addHeaders it
			}
			HttpResponse<?> response = client.toBlocking().exchange req
			assertEquals HttpStatus.OK, response.status
		}

		// Verify sheet appears in list
		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("userdata/sheets")).tap {
				addHeaders it
			}

			HttpResponse<GetSheetsResponse> response = client.toBlocking().exchange req, GetSheetsResponse
			assertEquals HttpStatus.OK, response.status
			assertEquals 1, response.body().sheets.size()
			assertEquals 'Test Sheet', response.body().sheets[0].name
			assertNull response.body().sheets[0].sortOrder
		}

		// Get specific sheet
		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("userdata/sheets/${sheetKey}")).tap {
				addHeaders it
			}

			HttpResponse<GetSheetResponse> response = client.toBlocking().exchange req, GetSheetResponse
			assertEquals HttpStatus.OK, response.status
			assertNotNull response.body()
			assertEquals([foo: 'bar'], response.body().sheetData)
			assertEquals 6, response.body().metadata.version
			assertNull response.body().metadata.sortOrder
		}

		// Try updating with outdated version
		// Server has version 6 at this point, so if the client thinks the server only has version 3, then
		{
			var sheetReq = new PutSheetRequest().tap {
				sheetName = 'Test Sheet Updated'
				sheetData = [foo: 'baz']
				lastSyncedVersion = 3
				newSheetVersion = 7    // Trying to set new version while server has 6
			}

			HttpRequest<PutSheetRequest> req = HttpRequest.PUT(server.URI.resolve("userdata/sheets/${sheetKey}"), sheetReq).tap {
				addHeaders it
			}
			HttpResponse<?> response = client.toBlocking().exchange req, Argument.of(String), Argument.of(String)
			assertEquals HttpStatus.CONFLICT, response.status
		}
	}
	
	@Test
	void testUnauth() {
		

		HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("userdata/preferences")).tap {
			header 'xivgear-csrf', '1'
		}
		HttpResponse<?> response = client.toBlocking().exchange req, Argument.of(String), Argument.of(String)
		assertEquals HttpStatus.UNAUTHORIZED, response.status

		req = HttpRequest.GET(server.URI.resolve("userdata/sheets")).tap {
			header 'xivgear-csrf', '1'
		}
		response = client.toBlocking().exchange req, Argument.of(String), Argument.of(String)
		assertEquals HttpStatus.UNAUTHORIZED, response.status

		var prefs = new UserPreferences().tap {
			lightMode = true
		}
		var prefsReq = new PutPreferencesRequest().tap {
			preferences = prefs
		}
		HttpRequest<PutPreferencesRequest> putReq = HttpRequest.PUT(server.URI.resolve("userdata/preferences"), prefsReq).tap {
			header 'xivgear-csrf', '1'
		}
		response = client.toBlocking().exchange putReq, Argument.of(String), Argument.of(String)
		assertEquals HttpStatus.UNAUTHORIZED, response.status
	}

	@Test
	void testValidTokenButNoCsrf() {
		HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("userdata/preferences")).tap {
			header 'Authorization', "Bearer ${validToken}"
		}
		HttpResponse<?> response = client.toBlocking().exchange req, Argument.of(String), Argument.of(String)
		assertEquals HttpStatus.FORBIDDEN, response.status

		req = HttpRequest.GET(server.URI.resolve("userdata/sheets")).tap {
			header 'Authorization', "Bearer ${validToken}"
		}
		response = client.toBlocking().exchange req, Argument.of(String), Argument.of(String)
		assertEquals HttpStatus.FORBIDDEN, response.status

		var prefs = new UserPreferences().tap {
			lightMode = true
		}
		var prefsReq = new PutPreferencesRequest().tap {
			preferences = prefs
		}
		HttpRequest<PutPreferencesRequest> putReq = HttpRequest.PUT(server.URI.resolve("userdata/preferences"), prefsReq).tap {
			header 'Authorization', "Bearer ${validToken}"
		}
		response = client.toBlocking().exchange putReq, Argument.of(String), Argument.of(String)
		assertEquals HttpStatus.FORBIDDEN, response.status
	}
}
