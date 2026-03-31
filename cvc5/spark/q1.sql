SELECT ((1+2)+3) FROM dept
SELECT (100 + 10 + sal) FROM emp
SELECT * FROM emp WHERE sal = 3 AND comm = sal + 5
SELECT * FROM emp WHERE sal = 3 AND NOT(comm = sal + 5)
SELECT (1*3+(deptno+2)+3*4) FROM dept
SELECT * FROM dept WHERE deptno IN (SELECT deptno FROM dept WHERE FALSE)
SELECT * FROM dept WHERE deptno IN (1, 2, 1, 1, 2)
SELECT * FROM dept AS dept WHERE dept.deptno = 1 AND TRUE
SELECT * FROM dept AS dept WHERE dept.deptno = 1 OR FALSE
SELECT * FROM dept AS dept WHERE dept.deptno = 1 AND FALSE
SELECT * FROM dept AS dept WHERE dept.deptno = 1 OR TRUE
SELECT * FROM ANON WHERE ANON.c > 1 AND ANON.c <= 1
SELECT * FROM emp WHERE emp.sal > 50 AND (emp.sal <= 50 OR emp.empno > 5)
SELECT * FROM emp WHERE emp.sal > 50 OR (emp.sal <= 50 AND emp.empno > 5)
SELECT * FROM emp AS emp WHERE (emp.sal = 50 OR emp.sal = 100 OR emp.empno > 5) AND (emp.sal = 50 OR emp.sal = 100)
SELECT * FROM emp AS emp WHERE (emp.sal = 50 AND emp.sal = 100 AND emp.empno > 5) OR (emp.sal = 50 AND emp.sal = 100)
SELECT dept.deptno <=> dept.deptno FROM dept AS dept
SELECT (deptno IS NULL) = (deptno IS NULL) FROM dept
SELECT (deptno IS NULL) > (deptno IS NULL) FROM dept
SELECT (deptno IS NULL) >= (deptno IS NULL) FROM dept
SELECT (deptno IS NULL) = TRUE FROM dept
SELECT (deptno IS NULL) = FALSE FROM dept
SELECT IF(2>1, deptno, CAST(name AS INT)) FROM dept
SELECT IF(null, CAST(deptno AS VARCHAR(10)), name) FROM dept
SELECT IF(deptno = 1, TRUE, FALSE) FROM dept
SELECT IF(deptno IS NULL, null, FALSE) FROM dept
SELECT IF(deptno IS NULL, FALSE, null) FROM dept
SELECT IF(deptno = 1, name, name) FROM dept
SELECT CASE WHEN (2>1) THEN deptno ELSE name END FROM dept
SELECT CASE WHEN deptno = 1 THEN TRUE ELSE FALSE END FROM dept
SELECT CASE WHEN deptno = 1 THEN deptno WHEN FALSE THEN name ELSE deptno+1 END FROM dept
SELECT CASE WHEN TRUE THEN deptno WHEN deptno = 1 THEN name ELSE deptno+1 END FROM dept
SELECT CASE WHEN deptno = 1 THEN deptno WHEN TRUE THEN name WHEN deptno = 10 THEN 10 ELSE deptno + 1 END FROM dept
SELECT CASE WHEN deptno = 1 THEN deptno WHEN name = 'Charlie' THEN deptno ELSE deptno END FROM dept
SELECT (IF(F0_C1=100, F1_C2, 2)=2) FROM T
SELECT (CASE WHEN F0_C1=100 THEN F1_C2 ELSE 2 END) = 2 FROM T
SELECT IF(F0_C1=100, F1_C2, 2) IS NULL FROM T
SELECT (CASE WHEN F0_C1=100 THEN F1_C2 ELSE 2 END) IS NULL FROM T
SELECT COUNT(DISTINCT (F1_C0 = NULL)) FROM T
SELECT COUNT(NULL) FROM T
SELECT * FROM EMP WHERE (NOT(null)) IS NULL
SELECT 1 AS X, 2 AS Y, empno FROM emp ORDER BY X, Y
SELECT CAST(deptno AS SIGNED) FROM dept
SELECT POSITIVE(deptno) FROM dept
SELECT UPPER(LOWER(name)) FROM dept
SELECT CONCAT("abc", CONCAT("def", "ghi")) AS c1 FROM dept
SELECT * FROM dept WHERE EXISTS (SELECT F0_C1 FROM T WHERE F1_C2 > 10)
SELECT * FROM emp AS emp, dept AS dept, account AS account WHERE emp.empno = dept.deptno AND emp.empno = account.acctno
SELECT T1.F0_C1, T1.F1_C0 FROM T AS T1 LEFT JOIN T AS T2 ON T1.F0_C1 = T2.F1_C0 WHERE T2.F0_C1 < 1
SELECT DISTINCT T1.F1_C0 FROM T AS T1 LEFT JOIN T AS T2 ON T1.F0_C1 = T2.F0_C1
SELECT *, ROW_NUMBER() OVER(ORDER BY F0_C1) AS rn FROM T LIMIT 5
SELECT * FROM T WHERE F0_C1 = -0.0
((SELECT * FROM T LIMIT 0) UNION ALL (SELECT * FROM T ORDER BY F0_C1 LIMIT 1)) ORDER BY F0_C1
SELECT F0_C1 FROM (SELECT F0_C1 FROM T LIMIT 1) T0 GROUP BY F0_C1
SELECT SUM(DISTINCT F0_C1) FROM (SELECT F0_C1 FROM T LIMIT 1) T0 GROUP BY F0_C1
SELECT MAX(DISTINCT deptno) FROM dept
SELECT COUNT(DISTINCT deptno) FILTER(WHERE 3>2) FROM dept
SELECT COUNT(DISTINCT deptno) FILTER(WHERE 3<2) FROM dept
SELECT dept.deptno AS deptno, dept.name AS name FROM dept AS dept
SELECT DISTINCT * FROM ((SELECT * FROM dept as dept0) UNION ALL (SELECT * FROM dept as dept1)) t0
(SELECT deptno FROM dept) UNION ALL (SELECT deptno FROM dept) LIMIT 10
SELECT * FROM (dept AS T1 LEFT OUTER JOIN dept AS T2 on T1.deptno = T2.deptno) LIMIT 10
SELECT t0.name FROM ((SELECT * FROM dept as dept0) UNION ALL (SELECT * FROM dept as dept1)) t0
SELECT emp1.ename, dept1.name FROM emp AS emp1 INNER JOIN dept AS dept1 ON emp1.sal > 50
SELECT dept0.name FROM (SELECT dept.deptno, dept.name FROM dept AS dept) dept0 WHERE dept0.deptno > 1
SELECT t0.c1 FROM (SELECT 1 AS c1, 2 AS c2 FROM dept AS dept) AS t0
SELECT t0.name FROM (SELECT dept.name, dept.deptno FROM dept AS dept) AS t0
SELECT * FROM dept WHERE deptno > 1
SELECT * FROM emp INNER JOIN dept ON emp.empno = dept.deptno AND emp.empno = 1
SELECT * FROM emp LEFT JOIN dept ON emp.empno = dept.deptno AND emp.empno = 1
SELECT tmp.empno, tmp.sal FROM (SELECT emp.empno, emp.sal FROM emp AS emp WHERE emp.sal = 3) AS tmp WHERE tmp.empno > 1
SELECT emp.empno FROM emp ORDER BY 'a'
SELECT emp.empno FROM emp ORDER BY 'a', emp.ename
SELECT t0.empno FROM (SELECT emp.empno FROM emp ORDER BY emp.empno) t0 ORDER BY t0.empno
SELECT * FROM (SELECT * FROM dept ORDER BY deptno) d1 INNER JOIN (SELECT * FROM dept ORDER BY deptno) d2
SELECT MIN(t0.empno) FROM (SELECT * FROM emp ORDER BY emp.empno) t0
SELECT * FROM emp AS emp WHERE TRUE
SELECT * FROM (SELECT * FROM emp AS emp WHERE emp.empno > 10) t0 WHERE t0.empno > 10
SELECT * FROM (SELECT * FROM emp AS emp WHERE emp.empno > 10) t0 WHERE t0.empno > 10 AND t0.empno > 0
SELECT * FROM (SELECT * FROM emp AS emp WHERE emp.empno > 10) t0 WHERE t0.empno > 10 AND t0.empno > 0 AND t0.sal > 1
SELECT * FROM (SELECT * FROM dept AS dept UNION ALL SELECT * FROM dept AS dept0) t0 WHERE t0.deptno = 0
SELECT * FROM (SELECT emp.deptno, MIN(emp.sal) FROM emp AS emp GROUP BY emp.deptno) t0 WHERE t0.deptno > 10
SELECT * FROM (SELECT dept.deptno FROM dept AS dept) t0 WHERE t0.deptno > 10
SELECT * FROM (SELECT DISTINCT dept.deptno FROM dept AS dept) t0 WHERE t0.deptno > 10
SELECT * FROM emp AS emp INNER JOIN dept AS dept WHERE emp.empno > 0 AND dept.deptno > 0
SELECT * FROM emp RIGHT JOIN dept ON emp.deptno = dept.deptno WHERE emp.sal > 0 AND dept.deptno = 1
SELECT * FROM emp LEFT JOIN dept ON emp.deptno = dept.deptno WHERE emp.sal > 0 AND dept.deptno = 1
SELECT * FROM emp AS emp INNER JOIN dept AS dept ON emp.deptno = dept.deptno AND emp.sal > 0 AND dept.deptno = 1
SELECT * FROM emp RIGHT JOIN dept ON emp.deptno = dept.deptno AND emp.sal > 0 AND dept.deptno = 1
SELECT * FROM emp LEFT JOIN dept ON emp.deptno = dept.deptno AND emp.sal > 0 AND dept.deptno = 1
SELECT * FROM (SELECT * FROM emp ORDER BY empno LIMIT 1) t0 ORDER BY empno LIMIT 10
SELECT c1 FROM (VALUES (1, 2)) AS t0(c1, c2)
SELECT * FROM (VALUES (1, 2), (3, 3)) AS t0(c1, c2) WHERE t0.c1 = t0.c2
SELECT DISTINCT dept.deptno, dept.name FROM dept AS dept
SELECT empno FROM emp GROUP BY empno, 'a'
SELECT dept.deptno FROM dept AS dept GROUP BY dept.deptno, dept.deptno
SELECT * FROM dept LIMIT 0
SELECT * FROM dept AS dept WHERE FALSE UNION ALL SELECT * FROM dept AS dept0 WHERE FALSE
SELECT * FROM dept AS dept UNION ALL SELECT * FROM dept AS dept0 WHERE FALSE
SELECT * FROM dept UNION ALL SELECT * FROM dept WHERE NULL AND TRUE
SELECT * FROM dept AS dept UNION ALL SELECT * FROM dept AS dept0 UNION ALL SELECT * FROM dept AS dept1 WHERE FALSE
SELECT dept.deptno FROM dept AS dept WHERE FALSE UNION ALL SELECT emp.empno FROM emp AS emp
SELECT empno FROM emp GROUP BY empno, ename IS NULL
SELECT * FROM emp AS emp INNER JOIN dept AS dept ON (emp.empno < 10 AND dept.deptno < 10) OR (emp.empno > 20 AND dept.deptno > 20)
SELECT * FROM emp AS emp INNER JOIN dept AS dept ON (emp.empno > 10 AND emp.empno <= 20) OR (emp.empno > 20 AND dept.deptno > 20)
SELECT * FROM emp LEFT JOIN dept ON (emp.empno < 10 AND dept.deptno < 10) OR (emp.empno > 20 AND dept.deptno > 20)
SELECT * FROM emp RIGHT JOIN dept ON (emp.empno < 10 AND dept.deptno < 10) OR (emp.empno > 20 AND dept.deptno > 20)
SELECT COUNT(DISTINCT empno) FROM (SELECT empno FROM emp GROUP BY empno) t0
SELECT * FROM emp WHERE empno > 0 EXCEPT SELECT * FROM emp WHERE empno < 10
SELECT * FROM emp WHERE NULL AND TRUE
SELECT * FROM emp WHERE (empno = 0) OR NULL
SELECT * FROM emp WHERE emp.empno > 0 AND NULL
SELECT * FROM emp WHERE CASE WHEN empno = 1 THEN FALSE ELSE NULL END
SELECT * FROM emp WHERE CASE WHEN empno = 1 THEN NULL ELSE TRUE END
SELECT * FROM emp WHERE CASE WHEN empno = 1 OR NULL THEN sal < 10 ELSE sal > 10 END
SELECT * FROM emp INNER JOIN dept ON NOT emp.empno IN (dept.deptno, NULL)
SELECT * FROM emp WHERE IF(TRUE AND NULL, FALSE OR NULL, NULL AND NULL)
SELECT TRUE <=> ((empno = 1) OR NULL) FROM emp
SELECT t1.empno, t1.mgr FROM emp AS t1 WHERE t1.empno = (SELECT MAX(t2.deptno) FROM emp AS t2 WHERE t1.mgr = t2.sal)
SELECT t1.empno, (SELECT max(t2.deptno) FROM emp AS t2 WHERE t1.mgr = t2.sal) FROM emp AS t1
SELECT (SELECT max(t2.deptno) FROM emp AS t2 WHERE t1.mgr = t2.sal) as sub, max(t1.empno) FROM emp AS t1 GROUP BY sub
SELECT * FROM emp INNER JOIN LATERAL (SELECT * FROM dept WHERE emp.deptno = dept.deptno) t0
SELECT * FROM emp INNER JOIN LATERAL (SELECT 1) t0
SELECT * FROM emp WHERE (SELECT 1) = 1
SELECT * FROM emp WHERE CAST((empno IS NULL) AS SIGNED) > 1000
SELECT * FROM emp WHERE CAST((empno IS NULL) AS SIGNED) < 1000
SELECT FIRST_VALUE(emp.deptno) OVER(PARTITION BY emp.empno ORDER BY emp.ename ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) FROM emp
