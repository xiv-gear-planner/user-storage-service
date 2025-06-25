package app.xivgear

import app.xivgear.userstorage.nosql.SheetCol
import app.xivgear.userstorage.nosql.SheetsTable
import app.xivgear.userstorage.nosql.UserDataCol
import app.xivgear.userstorage.nosql.UserDataTable
import groovy.transform.CompileStatic
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import oracle.nosql.driver.ops.GetResult
import oracle.nosql.driver.values.BinaryValue
import oracle.nosql.driver.values.IntegerValue
import oracle.nosql.driver.values.MapValue
import org.junit.jupiter.api.Test

import java.nio.charset.StandardCharsets
import java.security.SecureRandom

import static org.junit.jupiter.api.Assertions.assertEquals

@MicronautTest
@CompileStatic
class DbTests {
	@Inject
	UserDataTable users
	@Inject
	SheetsTable sheets

	@Test
	void testTableFlow() {
		int uid = new SecureRandom().nextInt(0, 1_000_000)
		int nextSetId = 123
		users.putByPK(uid, [
				(UserDataCol.next_set_id): new IntegerValue(nextSetId),
				(UserDataCol.preferences): new MapValue().tap {
					put 'foo', 'bar'
				}
		])
		GetResult userGet = users.get(uid)
		assertEquals nextSetId, userGet.value.getInt(UserDataCol.next_set_id.name())
		assertEquals uid, userGet.value.getInt(UserDataCol.user_id.name())
		assertEquals 'bar', userGet.value.get(UserDataCol.preferences.name()).asMap().getString('foo')

		String setPk = "set-save-456-asdf"
		sheets.putByPK(uid, setPk, [
				(SheetCol.sheet_summary): new MapValue().tap {
					put "foo", "bar"
				},
				(SheetCol.sheet_data_compressed): new BinaryValue("foo bar".getBytes(StandardCharsets.UTF_8))
		])

		GetResult getSetResult = sheets.get uid, setPk
		assertEquals uid, getSetResult.value.getInt(UserDataCol.user_id.name())
		assertEquals setPk, getSetResult.value.getString(SheetCol.sheet_save_key.name())
		assertEquals '{"foo":"bar"}', getSetResult.value.get(SheetCol.sheet_summary.name()).toJson()
		assertEquals "foo bar", new String(getSetResult.value.getBinary(SheetCol.sheet_data_compressed.name()), StandardCharsets.UTF_8)


	}
}
