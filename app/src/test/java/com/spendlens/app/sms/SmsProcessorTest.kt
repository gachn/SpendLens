package com.spendlens.app.sms

import com.spendlens.app.data.db.TransactionDao
import com.spendlens.app.data.repository.TransactionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class SmsProcessorTest {

    @Test
    fun `salary regex matches standard keywords`() {
        val regex = SmsProcessor.SALARY_RE
        assertTrue(regex.containsMatchIn("Your salary of Rs. 50000 has been credited"))
        assertTrue(regex.containsMatchIn("Corporate payroll transfer received"))
        assertTrue(regex.containsMatchIn("Stipend credited to account"))
        assertTrue(regex.containsMatchIn("wages for May 2026"))
    }

    @Test
    fun `salary regex matches new expanded keywords`() {
        val regex = SmsProcessor.SALARY_RE
        assertTrue(regex.containsMatchIn("Rs.50000.00 credited. Info: SAL CREDIT"))
        assertTrue(regex.containsMatchIn("credited by VPS/SAL/EMPLOYER"))
        assertTrue(regex.containsMatchIn("Pension payment of Rs. 30000"))
        assertTrue(regex.containsMatchIn("Travel allowance credited"))
        assertTrue(regex.containsMatchIn("Reimbursement for expenses Rs. 1200"))
        assertTrue(regex.containsMatchIn("reimb for trip"))
        assertTrue(regex.containsMatchIn("Monthly payout Rs 45000"))
        assertTrue(regex.containsMatchIn("Direct deposit from company"))
        assertTrue(regex.containsMatchIn("Direct dep received"))
        assertTrue(regex.containsMatchIn("Credited via NACH payment"))
        assertTrue(regex.containsMatchIn("ECS credit of Rs. 15000"))
        assertTrue(regex.containsMatchIn("ACH credit of Rs. 50000"))
    }

    @Test
    fun `salary regex does not match non-matching words with same prefix or suffix`() {
        val regex = SmsProcessor.SALARY_RE
        // Test word boundaries
        assertFalse(regex.containsMatchIn("Big summer sale on clothing"))
        assertFalse(regex.containsMatchIn("How to achieve financial freedom"))
        assertFalse(regex.containsMatchIn("Vector scale is correct"))
        assertFalse(regex.containsMatchIn("special stipendial payment")) // stipendial contains stipend, but is not the word stipend itself
    }

    @Test
    fun `test hasHistoricalIncome returns true when count is positive`() = runTest {
        val fakeDao = Proxy.newProxyInstance(
            TransactionDao::class.java.classLoader,
            arrayOf(TransactionDao::class.java)
        ) { _, method, args ->
            if (method.name == "countIncomeTransactions") {
                val counterparty = args?.get(0) as String
                val categoryId = args.get(1) as Long
                if (counterparty == "ACME CORP" && categoryId == 9L) 1 else 0
            } else {
                null
            }
        } as TransactionDao

        val repo = TransactionRepository(fakeDao)
        assertTrue(repo.hasHistoricalIncome("ACME CORP", 9L))
        assertFalse(repo.hasHistoricalIncome("OTHER CORP", 9L))
    }
}
