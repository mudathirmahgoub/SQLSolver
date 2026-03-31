SELECT 6 FROM dept
SELECT (110 + sal) FROM emp
SELECT * FROM emp WHERE sal = 3 AND comm = 8
SELECT * FROM emp WHERE sal = 3 AND NOT(comm = 8)
SELECT deptno+17 from dept
SELECT * FROM dept WHERE FALSE
SELECT * FROM dept WHERE deptno IN (1, 2)
SELECT * FROM dept AS dept WHERE dept.deptno = 1
SELECT * FROM dept AS dept WHERE dept.deptno = 1
SELECT * FROM dept AS dept WHERE FALSE
SELECT * FROM dept AS dept WHERE TRUE
SELECT * FROM ANON WHERE CASE WHEN ANON.c IS NULL THEN NULL ELSE FALSE END
SELECT * FROM emp WHERE emp.sal > 50 AND emp.empno> 5
SELECT * FROM emp WHERE emp.sal > 50 OR emp.empno> 5
SELECT * FROM emp AS emp WHERE emp.sal = 50 OR emp.sal = 100
SELECT * FROM emp AS emp WHERE emp.sal = 50 AND emp.sal = 100
SELECT TRUE FROM dept AS dept
SELECT TRUE FROM dept
SELECT FALSE FROM dept
SELECT TRUE FROM dept
SELECT (deptno IS NULL) FROM dept
SELECT NOT(deptno IS NULL) FROM dept
SELECT deptno FROM dept
SELECT name FROM dept
SELECT (deptno = 1) <=> TRUE FROM dept
SELECT deptno IS NULL AND null FROM dept
SELECT NOT(deptno IS NULL) AND null FROM dept
SELECT name FROM dept
SELECT deptno FROM dept
SELECT (deptno = 1) <=> TRUE FROM dept
SELECT CASE WHEN deptno = 1 THEN deptno ELSE deptno+1 END FROM dept
SELECT deptno FROM dept
SELECT CASE WHEN deptno = 1 THEN deptno WHEN TRUE THEN name END FROM dept
SELECT deptno FROM dept
SELECT IF(F0_C1=100, F1_C2=2, 2=2) FROM T
SELECT CASE WHEN F0_C1=100 THEN F1_C2 = 2 ELSE 2 = 2 END FROM T
SELECT IF(F0_C1=100, F1_C2 IS NULL, 2 IS NULL) FROM T
SELECT CASE WHEN F0_C1=100 THEN F1_C2 IS NULL ELSE 2 IS NULL END FROM T
SELECT 0
SELECT 0
SELECT * FROM EMP WHERE null IS NULL
SELECT 1 AS X, 2 AS Y, empno FROM emp ORDER BY 1, 2
SELECT deptno FROM dept
SELECT deptno FROM dept
SELECT UPPER(name) FROM dept
SELECT CONCAT("abc", "def", "ghi") AS c1 FROM dept
SELECT * FROM dept WHERE (SELECT 1 FROM (SELECT F0_C1 FROM T WHERE F1_C2 > 10) t0 LIMIT 1) IS NOT NULL
SELECT emp.*, dept.*, account.* FROM emp AS emp INNER JOIN account AS account ON emp.empno = account.acctno INNER JOIN dept AS dept ON emp.empno = dept.deptno
SELECT T1.F0_C1, T1.F1_C0 FROM T AS T1 INNER JOIN (SELECT F1_C0 FROM T WHERE F0_C1 < 1) AS T2 ON T1.F0_C1 = T2.F1_C0
SELECT DISTINCT T1.F1_C0 FROM T AS T1
SELECT *, ROW_NUMBER() OVER(ORDER BY F0_C1) AS rn FROM (SELECT * FROM T ORDER BY F0_C1 LIMIT 5) t0
SELECT * FROM T WHERE F0_C1 = 0.0
(SELECT * FROM T LIMIT 0) UNION ALL (SELECT * FROM T ORDER BY F0_C1 LIMIT 1)
SELECT F0_C1 FROM (SELECT F0_C1 FROM T LIMIT 1) T0
SELECT SUM(F0_C1) FROM (SELECT F0_C1 FROM T LIMIT 1) T0 GROUP BY F0_C1
SELECT MAX(deptno) FROM dept
SELECT COUNT(DISTINCT deptno) FROM dept
SELECT 0
SELECT dept.deptno, dept.name FROM dept AS dept
SELECT DISTINCT * FROM dept as dept0
(SELECT deptno FROM dept LIMIT 10) UNION ALL (SELECT deptno FROM dept LIMIT 10) LIMIT 10
SELECT * FROM ((SELECT * FROM dept LIMIT 10) AS T1 LEFT OUTER JOIN dept AS T2 on T1.deptno = T2.deptno) LIMIT 10
SELECT t0.name FROM ((SELECT dept0.name FROM dept as dept0) UNION ALL (SELECT dept1.name FROM dept as dept1)) t0
SELECT emp1.ename, dept1.name FROM (SELECT emp.ename, emp.sal FROM emp AS emp) AS emp1 INNER JOIN (SELECT dept.name FROM dept AS dept) AS dept1 ON emp1.sal > 50
SELECT dept.name FROM dept AS dept WHERE dept.deptno > 1
SELECT 1 AS c1 FROM dept AS dept
SELECT dept.name FROM dept AS dept
SELECT * FROM dept WHERE deptno > 1 AND NOT(deptno IS NULL)
SELECT * FROM (SELECT * FROM emp WHERE NOT empno IS NULL AND empno = 1) emp0 INNER JOIN (SELECT * FROM dept WHERE NOT deptno IS NULL AND deptno = 1) dept0 ON emp0.empno = dept0.deptno AND emp0.empno = 1
SELECT * FROM emp emp0 LEFT JOIN (SELECT * FROM dept WHERE NOT deptno IS NULL AND deptno = 1) dept0 ON emp0.empno = dept0.deptno AND emp0.empno = 1
SELECT tmp.empno, tmp.sal FROM (SELECT emp.empno, emp.sal FROM emp AS emp WHERE emp.sal = 3 AND emp.empno > 1) AS tmp
SELECT emp.empno FROM emp
SELECT emp.empno FROM emp ORDER BY emp.ename
SELECT t0.empno FROM (SELECT emp.empno FROM emp) t0 ORDER BY t0.empno
SELECT * FROM (SELECT * FROM dept) d1 INNER JOIN (SELECT * FROM dept) d2
SELECT MIN(t0.empno) FROM (SELECT * FROM emp) t0
SELECT * FROM emp AS emp
SELECT * FROM (SELECT * FROM emp AS emp WHERE emp.empno > 10) t0
SELECT * FROM (SELECT * FROM emp AS emp WHERE emp.empno > 10) t0 WHERE t0.empno > 0
SELECT * FROM (SELECT * FROM emp AS emp WHERE emp.empno > 10) t0 WHERE t0.empno > 0 AND t0.sal > 1
SELECT * FROM dept AS dept WHERE dept.deptno = 0 UNION ALL SELECT * FROM dept AS dept0 WHERE dept0.deptno = 0
SELECT emp.deptno, MIN(emp.sal) FROM emp AS emp WHERE emp.deptno > 10 GROUP BY emp.deptno
SELECT dept.deptno FROM dept AS dept WHERE dept.deptno > 10
SELECT DISTINCT dept.deptno FROM dept AS dept WHERE dept.deptno > 10
SELECT * FROM (SELECT * FROM emp AS emp WHERE emp.empno > 0) t0 INNER JOIN (SELECT * FROM dept AS dept WHERE dept.deptno > 0) t1
SELECT * FROM emp RIGHT JOIN (SELECT * FROM dept WHERE dept.deptno = 1) t0 ON emp.deptno = t0.deptno WHERE emp.sal > 0
SELECT * FROM (SELECT * FROM emp WHERE emp.sal > 0) t0 LEFT JOIN dept ON t0.deptno = dept.deptno WHERE dept.deptno = 1
SELECT * FROM (SELECT * FROM emp AS emp WHERE emp.sal > 0) t0 INNER JOIN (SELECT * FROM dept AS dept WHERE dept.deptno = 1) t1 ON t0.deptno = t1.deptno
SELECT * FROM (SELECT * FROM emp WHERE emp.sal > 0) t0 RIGHT JOIN dept ON t0.deptno = dept.deptno AND dept.deptno = 1
SELECT * FROM emp LEFT JOIN (SELECT * FROM dept WHERE dept.deptno = 1) t0 ON emp.deptno = t0.deptno AND emp.sal > 0
SELECT * FROM (SELECT * FROM emp ORDER BY empno LIMIT 1) t0 ORDER BY empno
SELECT * FROM (VALUES (1)) AS t0(c1)
SELECT * FROM (VALUES (3, 3)) AS t0(c1, c2)
SELECT dept.deptno, dept.name FROM dept AS dept GROUP BY dept.deptno, dept.name
SELECT empno FROM emp GROUP BY empno
SELECT dept.deptno FROM dept AS dept GROUP BY dept.deptno
SELECT * FROM dept WHERE FALSE
SELECT * FROM dept AS dept WHERE FALSE
SELECT * FROM dept AS dept
SELECT * FROM dept
SELECT * FROM dept AS dept UNION ALL SELECT * FROM dept AS dept0
SELECT emp.empno AS deptno FROM emp AS emp
SELECT empno FROM (SELECT emp.*, ename IS NULL AS c0 FROM emp) t0 GROUP BY empno, c0
SELECT * FROM (SELECT * FROM emp AS emp WHERE emp.empno < 10 OR emp.empno > 20) emp0 INNER JOIN (SELECT * FROM dept AS dept WHERE dept.deptno < 10 OR dept.deptno > 20) dept0 ON (emp0.empno < 10 AND dept0.deptno < 10) OR (emp0.empno > 20 AND dept0.deptno > 20)
SELECT * FROM (SELECT * FROM emp AS emp WHERE (emp.empno > 10 AND emp.empno <= 20) OR emp.empno > 20) emp0 INNER JOIN dept AS dept ON (emp0.empno > 10 AND emp0.empno <= 20) OR (emp0.empno > 20 AND dept.deptno > 20)
SELECT * FROM emp emp0 LEFT JOIN (SELECT * FROM dept WHERE dept.deptno < 10 OR dept.deptno > 20) dept0 ON (emp0.empno < 10 AND dept0.deptno < 10) OR (emp0.empno > 20 AND dept0.deptno > 20)
SELECT * FROM (SELECT * FROM emp WHERE emp.empno < 10 OR emp.empno > 20) emp0 RIGHT JOIN dept dept0 ON (emp0.empno < 10 AND dept0.deptno < 10) OR (emp0.empno > 20 AND dept0.deptno > 20)
SELECT COUNT(DISTINCT empno) FROM (SELECT empno FROM emp) t0
SELECT DISTINCT * FROM emp WHERE empno > 0 AND NOT COALESCE(empno < 10, FALSE)
SELECT * FROM emp WHERE FALSE
SELECT * FROM emp WHERE (empno = 0) OR FALSE
SELECT * FROM emp WHERE emp.empno > 0 AND FALSE
SELECT * FROM emp WHERE CASE WHEN empno = 1 THEN FALSE ELSE FALSE END
SELECT * FROM emp WHERE CASE WHEN empno = 1 THEN FALSE ELSE TRUE END
SELECT * FROM emp WHERE CASE WHEN empno = 1 OR FALSE THEN sal < 10 ELSE sal > 10 END
SELECT * FROM emp INNER JOIN dept ON FALSE
SELECT * FROM emp WHERE IF(TRUE AND FALSE, FALSE OR FALSE, FALSE AND FALSE)
SELECT TRUE <=> ((empno = 1) OR FALSE) FROM emp
SELECT sub.a, sub.b FROM (SELECT t1.empno as a, t1.mgr as b, sub0.max_dno AS max_dno FROM emp AS t1 LEFT JOIN LATERAL (SELECT max(t2.deptno) as max_dno FROM emp AS t2 WHERE t1.mgr = t2.sal) sub0) sub WHERE sub.a = sub.max_dno
SELECT t1.empno, sub.max_dno FROM emp AS t1 LEFT JOIN LATERAL (SELECT max(t2.deptno) as max_dno FROM emp AS t2 WHERE t1.mgr = t2.sal) sub
SELECT sub.max_dno, max(t1.empno) FROM emp AS t1 LEFT JOIN LATERAL (SELECT max(t2.deptno) as max_dno FROM emp AS t2 WHERE t1.mgr = t2.sal) sub GROUP BY sub.max_dno
SELECT * FROM emp INNER JOIN dept ON emp.deptno = dept.deptno
SELECT emp.*, 1 FROM emp
SELECT * FROM emp WHERE 1 = 1
SELECT * FROM emp WHERE ((empno IS NULL) IS NULL) AND NULL
SELECT * FROM emp WHERE ((empno IS NULL) IS NOT NULL) OR NULL
SELECT NTH_VALUE(emp.deptno, 1) OVER(PARTITION BY emp.empno ORDER BY emp.ename ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) FROM emp